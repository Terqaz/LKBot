package com.my.threads;

import com.my.Bot;
import com.my.GroupsRepository;
import com.my.Utils;
import com.my.exceptions.AuthenticationException;
import com.my.exceptions.LkNotRespondingException;
import com.my.models.Group;
import com.my.services.vk.VkBotService;
import lombok.SneakyThrows;

import java.util.Calendar;
import java.util.GregorianCalendar;

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
                int second = calendar.get(Calendar.SECOND);
                int minute = calendar.get(Calendar.MINUTE);
                int hour = calendar.get(Calendar.HOUR_OF_DAY);
                int weekDay = (Utils.mapWeekDayFromCalendar(calendar) + 1) % 7;

                if (hour == 18) {
                    try {
                        Bot.actualizeWeekType();
                    } catch (AuthenticationException e) {
                        Bot.login(Bot.getGroupByGroupName().get("ПИ-19-1"));
                        Bot.actualizeWeekType();
                    } catch (LkNotRespondingException e) {
                        if (weekDay == 0) {
                            Bot.manualChangeWeekType();
                        }
                    }

                    for (Group group : Bot.getGroupByGroupName().values()) {
                        final String dayScheduleReport = Bot.getDayScheduleReport(weekDay, group);
                        if (!dayScheduleReport.isEmpty())
                            vkBot.sendMessageTo(group.getSchedulingEnabledUsers(),
                                    "Держи расписание на завтра ;-)\n" + dayScheduleReport);
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
}