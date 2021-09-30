package com.my.threads;

import com.my.GroupsRepository;
import com.my.Main;
import com.my.Utils;
import com.my.exceptions.AuthenticationException;
import com.my.exceptions.LkNotRespondingException;
import com.my.exceptions.ReloginNeedsException;
import com.my.models.Group;
import com.my.models.LoggedUser;
import com.my.models.Subject;
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
        Main.getGroupByGroupName().values().forEach(group -> {
            LoggedUser loggedUser = group.getLoggedUser();
            final GregorianCalendar calendar = new GregorianCalendar();
            if (isNotUpdateTime(group, calendar, calendar.getTime()))
                return;
            try {
                CompletableFuture.runAsync(() ->
                        updateGroupSubjects(newSemester, group, loggedUser));

            } catch (AuthenticationException e) {
                vkBot.sendMessageTo(loggedUser.getId(), "Не удалось обновить данные из ЛК");
                Main.rememberUpdateAuthDataMessage(group, group.getLoggedUser(), group.getName(), true);

            } catch (ReloginNeedsException e) {
                Main.login(group);
                updateGroupSubjects(newSemester, group, loggedUser);

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
        Main.login(group);
        final String report;
        if (Main.getActualSemester().equals(newSemester))
            report = sameSemesterUpdate(group);
        else
            report = newSemesterUpdate(newSemester, group);

        group.getLkParser().logout();

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
        Map<Integer, Subject> oldSubjectsById = new HashMap<>();
        for (Subject subject : oldSubjects)
            oldSubjectsById.put(subject.getId(), subject);

        final List<Subject> newSubjects = group.getLkParser().getNewSubjects(oldSubjects, group).stream()
                .map(subject -> {
                    final Set<String> oldDocumentNames = oldSubjectsById.get(subject.getId()).getDocumentNames();
                    // Загружаем еще раз документы, где они в первый раз оказались пустыми
                    if (subject.getDocumentNames().isEmpty() && !oldDocumentNames.isEmpty()) {
                        final Set<String> newDocumentNames =
                                group.getLkParser().getSubjectDocumentNames(subject.getLkId(), group);
                        if (newDocumentNames.isEmpty())
                            subject.setDocumentNames(oldDocumentNames);
                        else
                            subject.setDocumentNames(newDocumentNames);
                    }
                    return subject;
                }).collect(Collectors.toList());

        final var checkDate = new Date();
        groupsRepository.updateSubjects(group.getName(), newSubjects, checkDate);
        group.setLastCheckDate(checkDate);
        group.setSubjects(newSubjects);

        return ReportsMaker.getSubjects (
                Utils.removeOldDocuments(oldSubjects, newSubjects),
                group.getNextCheckDate());
    }

    private boolean allDocumentNamesIsEmpty(List<Subject> newSubjects) {
        return newSubjects.stream()
                .allMatch(subject ->
                        subject.getDocumentNames().isEmpty());
    }

    private String newSemesterUpdate(String newSemester, Group group) {
        Main.setActualSemester(newSemester);
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
        groupsRepository.setNewSemesterData(group.getName(), newSubjects, checkDate,
                lkParser.parseTimetable(newLkSemesterId, group.getLkId()),
                newLkSemesterId);
        return report;
    }
}