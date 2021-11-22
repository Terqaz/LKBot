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
import lombok.extern.log4j.Log4j2;

import javax.crypto.NoSuchPaddingException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.stream.Collectors;

@Log4j2
public class PlannedSubjectsUpdate extends Thread {

    private final GroupsRepository groupsRepository = GroupsRepository.getInstance();
    private final VkBotService vkBot = VkBotService.getInstance();
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
                sleep(50);

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void updateSubjects(String newSemester) {
        Bot.getGroupByGroupName().values().stream()
                .filter(group -> group.getLoggedUser() != null)
                .forEach(group -> {
                    final GregorianCalendar calendar = new GregorianCalendar();
                    if (isNotUpdateTime(group, calendar))
                        return;

                    try {
                        updateGroupSubjects(newSemester, group);

                    } catch (AuthenticationException e) {
                        Bot.rememberUpdateAuthDataMessage(group.getName(), group.getLoggedUser(), true);

                    } catch (LkNotRespondingException ignored) {
                    } catch (Exception e) {
                        log.error("Ошибка c обновлением предметов у группы: " + group.getName(), e);
                    }
                });
    }

    private boolean isNotUpdateTime (Group group, GregorianCalendar calendar) {
        return Utils.isSilentTime(group.getSilentModeStart(), group.getSilentModeEnd(),
                calendar.get(Calendar.HOUR_OF_DAY));
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

    private String sameSemesterUpdate(Group group) {
        if (Utils.isNullOrEmptyCollection(group.getSubjects())) {
            final String report = loadSubjectsFirstTime(group);
            if (!report.isEmpty()) return report;
        }
        loadLkIdsIfNeeds(group);

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
                    copyVkAttachments(newSubject.getMaterialsDocuments(), oldMaterialsDocuments);
                    return newSubject;

                }).collect(Collectors.toList());

        final List<Subject> cleanedSubjects = Utils.removeOldMaterialsDocuments(oldSubjects, newSubjects);

        if (!cleanedSubjects.stream()
                .allMatch(subject -> subject.getMaterialsDocuments().isEmpty() && subject.getMessagesData().isEmpty())) {
            groupsRepository.updateSubjects(group.getName(), newSubjects);
            group.setSubjects(newSubjects);
        }

        return Answer.getSubjects(cleanedSubjects);
    }

    // Changes newDocuments
    public static void copyVkAttachments(Collection<LkDocument> newDocuments,
                                         Collection<LkDocument> oldDocuments) {

        if (newDocuments.isEmpty() || oldDocuments.isEmpty()) return;

        final Map<String, LkDocument> newDocumentsMap =
                newDocuments.stream()
                        .collect(Collectors.toMap(LkDocument::getLkId, d -> d));

        oldDocuments.stream()
                .filter(lkDocument -> lkDocument.getVkAttachment() != null)
                .forEach(oldDocument -> {
                    final LkDocument newDocument = newDocumentsMap.get(oldDocument.getLkId());
                    if (newDocument != null)
                        newDocument.setVkAttachment(oldDocument.getVkAttachment())
                                .setIsExtChanged(oldDocument.getIsExtChanged());
                });
    }

    public String loadSubjectsFirstTime(Group group) {
        List<Subject> newSubjects = group.getLkParser().getSubjectsFirstTime(Bot.getActualSemester());

        if (Utils.isNullOrEmptyCollection(newSubjects))
            return "";

        groupsRepository.updateSubjects(group.getName(), newSubjects);
        group.setSubjects(newSubjects);
        return Answer.getSubjectsSuccessful(newSubjects);
    }

    public void loadLkIdsIfNeeds(Group group) {
        if (group.getLkId() != null && group.getLkSemesterId() != null && group.getLkContingentId() != null)
            return;

        group.getLkParser().setSubjectsGeneralLkIds(group, Bot.getActualSemester());
        groupsRepository.updateLkIds(group.getName(),
                group.getLkId(), group.getLkSemesterId(), group.getLkContingentId());
    }

    private String newSemesterUpdate(String newSemester, Group group) {
        Bot.setActualSemester(newSemester);
        vkBot.sendMessageTo(group.getUserIds(),
                "Данные теперь будут приходить из семестра: " + newSemester + "\n" +
                        "Советую тебе обновить пароль в ЛК ;-) (http://lk.stu.lipetsk.ru/)");

        final LkParser lkParser = group.getLkParser();
        final List<Subject> newSubjects = lkParser.getSubjectsFirstTime(newSemester);

        final String report = Answer.getSubjects(newSubjects);

        lkParser.setSubjectsGeneralLkIds(group, newSemester);
        final var newLkSemesterId = group.getLkSemesterId();

        Timetable timetable = lkParser.parseTimetable(newLkSemesterId, group.getLkId());

        groupsRepository.setNewSemesterData(group.getName(), newSubjects,
                timetable,
                newLkSemesterId, group.getLkContingentId());

        group.setNewSemesterData(newSubjects, timetable);
        return report;
    }
}