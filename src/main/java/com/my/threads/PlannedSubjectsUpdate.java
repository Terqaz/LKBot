package com.my.threads;

import com.my.Bot;
import com.my.GroupsRepository;
import com.my.Utils;
import com.my.exceptions.AuthenticationException;
import com.my.exceptions.LkNotRespondingException;
import com.my.exceptions.LoginNeedsException;
import com.my.models.Group;
import com.my.models.LoggedUser;
import com.my.models.Subject;
import com.my.models.Timetable;
import com.my.services.CipherService;
import com.my.services.ReportsMaker;
import com.my.services.lk.LkParser;
import com.my.services.vk.VkBotService;
import lombok.SneakyThrows;

import javax.crypto.NoSuchPaddingException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class PlannedSubjectsUpdate extends Thread {

    private static final GroupsRepository groupsRepository = GroupsRepository.getInstance();
    private static final VkBotService vkBot = VkBotService.getInstance();
    CipherService cipherService;

    public PlannedSubjectsUpdate() throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeySpecException {
        cipherService = CipherService.getInstance();
        start();
    }

    @SneakyThrows
    @Override
    public void run() {
        while (true) {
            try {
                updateSubjects(Utils.getSemesterName());
                Thread.sleep(60L * 1000); // 1 минута

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void updateSubjects(String newSemester) {
        Bot.getGroupByGroupName().values().forEach(group -> {
            LoggedUser loggedUser = group.getLoggedUser();
            final GregorianCalendar calendar = new GregorianCalendar();
            if (isNotUpdateTime(group, calendar, calendar.getTime()))
                return;
            try {
                CompletableFuture.runAsync(() ->
                        updateGroupSubjects(newSemester, group, loggedUser));

            } catch (AuthenticationException e) {
                vkBot.sendMessageTo(loggedUser.getId(), "Не удалось обновить данные из ЛК");
                Bot.rememberUpdateAuthDataMessage(group.getName(), group.getLoggedUser(), true);

            } catch (LoginNeedsException e) {
                Bot.login(group);
                CompletableFuture.runAsync(() ->
                        updateGroupSubjects(newSemester, group, loggedUser));

            } catch (LkNotRespondingException e) {
                if (loggedUser.isAlwaysNotify()) {
                    group.setLastCheckDate(new Date());
                    vkBot.sendMessageTo(loggedUser.getId(),
                            "Обновление не удалось, так как ЛК долго отвечал на запросы. " +
                                    ReportsMaker.getNextUpdateDateText(group.getNextCheckDate()));
                }
            }
        });
    }

    private void updateGroupSubjects(String newSemester, Group group, LoggedUser loggedUser) {
        Bot.login(group);
        final String report;
        if (Bot.getActualSemester().equals(newSemester))
            report = sameSemesterUpdate(group);
        else
            report = newSemesterUpdate(newSemester, group);

        if (!report.startsWith("Нет новой"))
            vkBot.sendLongMessageTo(group.getUserIds(), report);

        else if (loggedUser.isAlwaysNotify())
            vkBot.sendMessageTo(loggedUser.getId(), report);
    }

    private boolean isNotUpdateTime (Group group, GregorianCalendar calendar, Date checkDate) {
        return Utils.isSilentTime(group.getSilentModeStart(), group.getSilentModeEnd(),
                calendar.get(Calendar.HOUR_OF_DAY)) || !checkDate.after(group.getNextCheckDate());
    }

    private String sameSemesterUpdate(Group group) {
        final List<Subject> oldSubjects = group.getSubjects();
        List<Subject> newSubjects = group.getLkParser().getNewSubjects(oldSubjects, group);

        Map<Integer, Subject> newSubjectsById = newSubjects.stream()
                .collect(Collectors.toMap(Subject::getId, subject -> subject));

        newSubjects = oldSubjects.stream()
            .map(oldSubject -> { // На всякий
                Subject newSubject = newSubjectsById.get(oldSubject.getId());
                if (!oldSubject.getDocumentNames().isEmpty() && newSubject.getDocumentNames().isEmpty())
                        newSubject.setDocumentNames(oldSubject.getDocumentNames());
                return newSubject;
            }).collect(Collectors.toList());

        final var checkDate = new Date();
        groupsRepository.updateSubjects(group.getName(), newSubjects, checkDate);
        group.setLastCheckDate(checkDate);
        group.setSubjects(newSubjects);

        return ReportsMaker.getSubjects (
                Utils.removeOldDocuments(oldSubjects, newSubjects),
                group.getNextCheckDate());
    }

    private String newSemesterUpdate(String newSemester, Group group) {
        Bot.setActualSemester(newSemester);
        vkBot.sendMessageTo(group.getUserIds(),
                "Данные теперь приходят из семестра: " + newSemester + "\n" +
                        "Также советую тебе обновить пароль в ЛК ;-) (http://lk.stu.lipetsk.ru/)");

        final LkParser lkParser = group.getLkParser();
        final List<Subject> newSubjects = lkParser.getSubjectsFirstTime(newSemester);

        final var checkDate = new Date();
        group.setLastCheckDate(checkDate);
        final String report = ReportsMaker.getSubjects(newSubjects, group.getNextCheckDate());

        final Map<String, String> lkIds = lkParser.getSubjectsGeneralLkIds(newSemester);
        final var newLkSemesterId = lkIds.get(LkParser.SEMESTER_ID);

        Timetable timetable = lkParser.parseTimetable(newLkSemesterId, group.getLkId());

        groupsRepository.setNewSemesterData(group.getName(),
                newSubjects, checkDate, timetable, newLkSemesterId);
        group.setNewSemesterData(newSubjects, timetable, newLkSemesterId);

        return report;
    }
}