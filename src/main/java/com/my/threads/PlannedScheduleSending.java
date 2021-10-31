package com.my.threads;

import com.my.Bot;
import com.my.GroupsRepository;
import com.my.Utils;
import com.my.exceptions.AuthenticationException;
import com.my.exceptions.LkNotRespondingException;
import com.my.models.Group;
import com.my.models.GroupUser;
import com.my.models.Timetable;
import com.my.services.Answer;
import com.my.services.vk.VkBotService;
import lombok.SneakyThrows;

import java.util.GregorianCalendar;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.Calendar.*;

public class PlannedScheduleSending extends Thread {

    static final GroupsRepository groupsRepository = GroupsRepository.getInstance();
    static final VkBotService vkBot = VkBotService.getInstance();

    public PlannedScheduleSending() {
        start();
    }

    @SneakyThrows
    @Override
    public void run() {
        while (true) {
            try {
                GregorianCalendar calendar = new GregorianCalendar();
                int second = calendar.get(SECOND);
                int minute = calendar.get(MINUTE);
                int hour = calendar.get(HOUR_OF_DAY);
                int nextWeekDay = Utils.getNextWeekDayIndex(calendar);

                if (hour == 18) {
                    try {
                        Bot.actualizeWeekType();
                    } catch (AuthenticationException e) {
                        Bot.login(Bot.getGroupByGroupName().get("ПИ-19-1"));
                        Bot.actualizeWeekType();
                    } catch (LkNotRespondingException e) {
                        if (calendar.get(DAY_OF_WEEK) == MONDAY)
                            Bot.manualChangeWeekType();
                    }
                    final boolean isNextDayWeekWhite =
                            nextWeekDay == 0 ? !Bot.isActualWeekWhite() : Bot.isActualWeekWhite();

                    Bot.getGroupByGroupName().values().stream()
                            .forEach(group -> CompletableFuture.runAsync(() -> {
                                while(!sendSchedule(group, nextWeekDay, isNextDayWeekWhite)) {
                                    try {
                                        Thread.sleep(5L * 60 * 1000); // 5 минут
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }));
                    Thread.sleep(5L * 60 * 1000); // 5 минут

                } else if (hour == 19) {
                    Bot.getGroupByGroupName().values().forEach(group -> group.setScheduleSent(false));
                    groupsRepository.updateEachField(GroupsRepository.SCHEDULE_SENT, false);
                } else
                    Thread.sleep(Utils.getSleepTimeToHourStart(minute, second));

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private boolean sendSchedule(Group group, int nextWeekDay, boolean isNextDayWeekWhite) {
        try {
            Bot.login(group);
            final Timetable timetable = group.getLkParser()
                    .parseTimetable(group.getLkSemesterId(), group.getLkId());

            if (isNewTimetableCorrect(timetable, group.getTimetable())) {
                groupsRepository.updateField(group.getName(), GroupsRepository.TIMETABLE, timetable);
                group.setTimetable(timetable);
            }
            // Если не получилось обновить, то отправим предыдущее расписание. Расписание меняется очень редко
        } catch (Exception e) {
            e.printStackTrace();
        }

        final String dayScheduleReport = Bot.getDayScheduleReport(nextWeekDay, isNextDayWeekWhite, group);
        if (!dayScheduleReport.isEmpty()) {
            vkBot.sendMessageTo(
                    group.getUsers().stream()
                            .filter(GroupUser::isEverydayScheduleEnabled)
                            .map(GroupUser::getId)
                            .collect(Collectors.toList()),
                    Answer.getTomorrowSchedule(dayScheduleReport));

            group.setScheduleSent(true);
            groupsRepository.updateField(group.getName(), GroupsRepository.SCHEDULE_SENT, true);
        }
        return group.isScheduleSent();
    }

    private boolean isNewTimetableCorrect (Timetable newTt, Timetable oldTt) {
        // Эмпирическое правило
        return newTt.getWhiteSubjects().size() + newTt.getGreenSubjects().size() >=
                oldTt.getWhiteSubjects().size() + oldTt.getGreenSubjects().size() - 2;
    }
}