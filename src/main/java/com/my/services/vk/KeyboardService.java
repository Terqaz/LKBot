package com.my.services.vk;

import com.my.models.Command;
import com.my.models.Group;
import com.vk.api.sdk.objects.messages.*;

import java.util.List;

public class KeyboardService {

    private KeyboardService () {}

    public static KeyboardButton generateButton (String command, KeyboardButtonColor color) {
        return new KeyboardButton()
                .setAction(new KeyboardButtonAction()
                        .setLabel(command)
                        .setType(TemplateActionTypeNames.TEXT))
                .setColor(color);
    }

    public static final Keyboard KEYBOARD_1 = new Keyboard()
            .setButtons(List.of(
                    List.of(generateButton(Command.WANT_TO_LOGIN, KeyboardButtonColor.POSITIVE)),
                    List.of(generateButton(Command.CHANGE_GROUP, KeyboardButtonColor.DEFAULT))
            ));

    public static final Keyboard KEYBOARD_2 = new Keyboard()
            .setButtons(List.of(
                    List.of(generateButton(Command.CHANGE_GROUP, KeyboardButtonColor.DEFAULT))
            ));

    public static final Keyboard KEYBOARD_3 = new Keyboard()
            .setButtons(List.of(
                    List.of(generateButton(Command.WANT_TO_LOGIN, KeyboardButtonColor.POSITIVE)),
                    List.of(generateButton(Command.FORGET_ME, KeyboardButtonColor.DEFAULT))
            ));

    private static final KeyboardButton subjectsButton =
            generateButton(Command.GET_SUBJECTS, KeyboardButtonColor.PRIMARY);

    private static final KeyboardButton commandsButton =
            generateButton(Command.COMMANDS, KeyboardButtonColor.PRIMARY);

    private static final KeyboardButton scheduleEnableButton =
            generateButton(Command.WITH_EVERYDAY_SCHEDULE, KeyboardButtonColor.POSITIVE);

    private static final KeyboardButton scheduleDisableButton =
            generateButton(Command.WITHOUT_EVERYDAY_SCHEDULE, KeyboardButtonColor.NEGATIVE);

    private static final KeyboardButton forgetMeButton =
            generateButton(Command.FORGET_ME, KeyboardButtonColor.DEFAULT);

    public static Keyboard getCommands(Integer userId, Group group) {
        boolean isSchedulingEnabled = group.getUserSchedulingEnabled(userId);
        return group.getLoggedUser().is(userId) ? getLoggedUserCommands(isSchedulingEnabled) :
                                       getUserCommands(isSchedulingEnabled);
    }

    private static Keyboard getLoggedUserCommands(boolean isSchedulingEnabled) {
        return new Keyboard().setButtons(List.of(
                List.of(subjectsButton, commandsButton),
                List.of(isSchedulingEnabled ? scheduleDisableButton : scheduleEnableButton),
                List.of(forgetMeButton)));
    }

    private static Keyboard getUserCommands(boolean isSchedulingEnabled) {
        return new Keyboard().setButtons(List.of(
                List.of(subjectsButton, commandsButton),
                List.of(isSchedulingEnabled ? scheduleDisableButton : scheduleEnableButton),
                List.of(forgetMeButton)));
    }
}
