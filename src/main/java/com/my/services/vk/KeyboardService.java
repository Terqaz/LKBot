package com.my.services.vk;

import com.my.models.Group;
import com.my.models.LoggedUser;
import com.vk.api.sdk.objects.messages.*;

import java.util.List;

public class KeyboardService {

    private KeyboardService () {}

    public static KeyboardButton generateButton (String text, KeyboardButtonColor color) {
        return new KeyboardButton()
                .setAction(new KeyboardButtonAction()
                        .setLabel(text)
                        .setType(TemplateActionTypeNames.TEXT))
                .setColor(color);
    }

    public static final Keyboard KEYBOARD_2 = new Keyboard()
            .setButtons(List.of(
                    List.of(generateButton("Измени группу", KeyboardButtonColor.DEFAULT))
            ));


    private static final KeyboardButton subjectsButton =
            generateButton("Предметы", KeyboardButtonColor.PRIMARY);

    private static final KeyboardButton commandsButton =
            generateButton("Команды", KeyboardButtonColor.PRIMARY);

    private static final KeyboardButton forgetMeButton =
            generateButton("Забудь меня", KeyboardButtonColor.DEFAULT);

    private static final KeyboardButton withoutEmptyReportsButton =
            generateButton("Без пустых отчетов", KeyboardButtonColor.NEGATIVE);

    private static final KeyboardButton withEmptyReportsButton =
            generateButton("С пустыми отчетами", KeyboardButtonColor.POSITIVE);

    private static final KeyboardButton timetableUpdateButton =
            generateButton("Обновить расписание", KeyboardButtonColor.DEFAULT);

    private static final KeyboardButton scheduleEnableButton =
            generateButton("Присылай расписание", KeyboardButtonColor.POSITIVE);

    private static final KeyboardButton scheduleDisableButton =
            generateButton("Не присылай расписание", KeyboardButtonColor.NEGATIVE);

    public static Keyboard getCommands(Integer userId, Group group) {
        boolean isSchedulingEnabled = group.getUserSchedulingEnabled(userId);
        return group.getLoggedUser().is(userId) ? getLoggedUserCommands(group.getLoggedUser(), isSchedulingEnabled) :
                                       getUserCommands(isSchedulingEnabled);
    }

    public static Keyboard getLoggedUserCommands(Group group) {
        LoggedUser loggedUser = group.getLoggedUser();
        return getLoggedUserCommands(loggedUser, group.getUserSchedulingEnabled(loggedUser.getId()));
    }

    private static Keyboard getLoggedUserCommands(LoggedUser loggedUser, boolean isSchedulingEnabled) {
        return new Keyboard().setButtons(List.of(
                List.of(subjectsButton, commandsButton),
                List.of(timetableUpdateButton, isSchedulingEnabled ? scheduleDisableButton : scheduleEnableButton),
                List.of(forgetMeButton,
                        loggedUser.isAlwaysNotify() ? withoutEmptyReportsButton : withEmptyReportsButton)));
    }

    private static Keyboard getUserCommands(boolean isSchedulingEnabled) {
        return new Keyboard().setButtons(List.of(
                List.of(subjectsButton, commandsButton),
                List.of(isSchedulingEnabled ? scheduleDisableButton : scheduleEnableButton),
                List.of(forgetMeButton)));
    }
}
