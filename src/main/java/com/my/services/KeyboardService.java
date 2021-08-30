package com.my.services;

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

    public static final Keyboard KEYBOARD_1 = new Keyboard()
            .setButtons(List.of(
                    List.of(generateButton("Я готов на все ради своей группы!", KeyboardButtonColor.POSITIVE)),
                    List.of(generateButton("Лучше скажу другому", KeyboardButtonColor.NEGATIVE)),
                    List.of(generateButton("Я ошибся при вводе группы", KeyboardButtonColor.DEFAULT))
            ));

    public static final Keyboard KEYBOARD_2 = new Keyboard()
            .setButtons(List.of(
                    List.of(generateButton("Я ошибся при вводе группы", KeyboardButtonColor.DEFAULT))
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

    public static Keyboard getCommandsKeyboard (Integer userId, LoggedUser loggedUser) {
        if (loggedUser.is(userId)) {
            return new Keyboard().setButtons(List.of(
                    List.of(subjectsButton, commandsButton),
                    List.of(forgetMeButton,
                            loggedUser.isAlwaysNotify() ? withoutEmptyReportsButton : withEmptyReportsButton)));
        } else
            return new Keyboard().setButtons(List.of(
                    List.of(subjectsButton, commandsButton),
                    List.of(forgetMeButton)));
    }
}
