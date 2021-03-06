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

                    for (Group group : Bot.getGroupByGroupName().values()) {
                        CompletableFuture.runAsync(() -> {
                            updateSchedule(group);

                            boolean isNextDayWeekWhite = Bot.isActualWeekWhite();
                            if (nextWeekDay == 0) // Если следующий день
                                isNextDayWeekWhite = !isNextDayWeekWhite;

                            final String dayScheduleReport =
                                    Bot.getDayScheduleReport(nextWeekDay, isNextDayWeekWhite, group);

                            if (!dayScheduleReport.isEmpty())
                                vkBot.sendMessageTo(
                                        group.getUsers().stream()
                                                .filter(GroupUser::isEverydayScheduleEnabled)
                                                .map(GroupUser::getId)
                                                .collect(Collectors.toList()),
                                        Answer.getTomorrowSchedule(dayScheduleReport));
                        });
                    }
                    Thread.sleep(3600L * 1000); // 1 час

                } else Thread.sleep(Utils.getSleepTimeToHourStart(minute, second));

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void updateSchedule(Group group) {
        try {
            Bot.login(group);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        final Timetable timetable = group.getLkParser().parseTimetable(group.getLkSemesterId(), group.getLkId());
        groupsRepository.updateField(group.getName(),"timetable", timetable);
        group.setTimetable(timetable);
    }
}