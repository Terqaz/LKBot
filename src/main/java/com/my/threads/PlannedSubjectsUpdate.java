package com.my.threads;

import com.my.Bot;
import com.my.GroupsRepository;
import com.my.Utils;
import com.my.exceptions.AuthenticationException;
import com.my.exceptions.LkNotRespondingException;
import com.my.models.Group;
import com.my.models.LkDocument;
import com.my.models.Subject;
import com.my.models.Timetable;
import com.my.services.Answer;
import com.my.services.CipherService;
import com.my.services.lk.LkParser;
import com.my.services.vk.VkBotService;
import lombok.SneakyThrows;

import javax.crypto.NoSuchPaddingException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
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
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void updateSubjects(String newSemester) {
        Bot.getGroupByGroupName().values().stream()
                .filter(group -> !group.isUpdating())
                .forEach(group -> {
                    group.setUpdating(true);
                    final GregorianCalendar calendar = new GregorianCalendar();
                    if (isNotUpdateTime(group, calendar))
                        return;

                    try {
                        updateGroupSubjects(newSemester, group);
                    } catch (AuthenticationException e) {
                        Bot.rememberUpdateAuthDataMessage(group.getName(), group.getLoggedUser(), true);
                    } catch (LkNotRespondingException e) {
                        group.setLastCheckDate(new Date());
                    } finally {
                        group.setUpdating(false);
                    }
        });
    }

    private void updateGroupSubjects(String newSemester, Group group) {
        Bot.login(group);
        final String report;
        if (Bot.getActualSemester().equals(newSemester))
            report = sameSemesterUpdate(group);
        else
            report = newSemesterUpdate(newSemester, group);

        if (!report.isBlank())
            vkBot.sendLongMessageTo(group.getUserIds(), report);
    }

    private boolean isNotUpdateTime (Group group, GregorianCalendar calendar) {
        return Utils.isSilentTime(group.getSilentModeStart(), group.getSilentModeEnd(),
                calendar.get(Calendar.HOUR_OF_DAY));
    }

    private String sameSemesterUpdate(Group group) {
        final List<Subject> oldSubjects = group.getSubjects();
        List<Subject> newSubjects = group.getLkParser().getNewSubjects(oldSubjects, group);

        Map<Integer, Subject> newSubjectsById = newSubjects.stream()
                .collect(Collectors.toMap(Subject::getId, subject -> subject));

        newSubjects = oldSubjects.stream() // На всякий
                .map(oldSubject -> {
                    Subject newSubject = newSubjectsById.get(oldSubject.getId());

                    final Set<LkDocument> oldMaterialsDocuments = oldSubject.getMaterialsDocuments();
                    if (!oldMaterialsDocuments.isEmpty() && newSubject.getMaterialsDocuments().isEmpty())
                            newSubject.setMaterialsDocuments(oldMaterialsDocuments);

                    newSubject.getMessagesDocuments().addAll(oldSubject.getMessagesDocuments());
                    Utils.copyIdsFrom(
                            newSubject.getMaterialsDocuments(), oldSubject.getMaterialsDocuments());
                    Utils.setIdsWhereNull(newSubject);
                    return newSubject;

                }).collect(Collectors.toList());

        final var checkDate = new Date();

        final List<Subject> cleanedSubjects = Utils.removeOldDocuments(oldSubjects, newSubjects);

        if (!cleanedSubjects.stream()
                .allMatch(subject -> subject.getMaterialsDocuments().isEmpty() && subject.getMessagesData().isEmpty())) {
            groupsRepository.updateSubjects(group.getName(), newSubjects, checkDate);
            group.setSubjects(newSubjects);
        } else
            groupsRepository.updateLastCheckDate(group.getName(), checkDate);

        group.setLastCheckDate(checkDate);
        return Answer.getSubjects(cleanedSubjects);
    }

    private String newSemesterUpdate(String newSemester, Group group) {
        Bot.setActualSemester(newSemester);
        vkBot.sendMessageTo(group.getUserIds(),
                "Данные теперь будут приходить из семестра: " + newSemester + "\n" +
                        "Советую тебе обновить пароль в ЛК ;-) (http://lk.stu.lipetsk.ru/)");

        final LkParser lkParser = group.getLkParser();
        final List<Subject> newSubjects = lkParser.getSubjectsFirstTime(newSemester);

        final var checkDate = new Date();
        group.setLastCheckDate(checkDate);
        final String report = Answer.getSubjects(newSubjects);

        final Map<String, String> lkIds = lkParser.getSubjectsGeneralLkIds(newSemester);
        final var newLkSemesterId = lkIds.get(LkParser.SEMESTER_ID);

        Timetable timetable = lkParser.parseTimetable(newLkSemesterId, group.getLkId());

        groupsRepository.setNewSemesterData(group.getName(),
                newSubjects, checkDate, timetable, newLkSemesterId);
        group.setNewSemesterData(newSubjects, timetable, newLkSemesterId);

        return report;
    }
}