package com.my.threads;

import com.my.GroupsRepository;
import com.my.Main;
import com.my.Utils;
import com.my.models.Group;
import com.my.models.GroupUser;
import com.my.services.vk.VkBotService;
import lombok.SneakyThrows;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.stream.Collectors;

public class PlannedScheduleSending extends Thread {

    static final GroupsRepository groupsRepository = GroupsRepository.getInstance();
    static final VkBotService vkBot = VkBotService.getInstance();
    private boolean isActualWeekWhite;

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

                if (weekDay == 0 && hour == 0) {
                    Main.actualizeWeekType();
                    Thread.sleep(3600L * 1000); // 1 час

                } else if (hour == 18) {
                    for (Group group : groupsRepository.findAllWithoutSubjects()) {
                        final String dayScheduleReport = Main.getDayScheduleReport(weekDay, group);
                        if (!dayScheduleReport.isEmpty())
                            vkBot.sendMessageTo(
                                    group.getUsers().stream()
                                            .filter(GroupUser::isEverydayScheduleEnabled)
                                            .map(GroupUser::getId)
                                            .collect(Collectors.toList()),
                                    "Держи расписание на завтра ;-) "+dayScheduleReport);
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