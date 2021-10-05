package com.my.threads;

import com.my.Main;
import com.my.TestUtils;
import com.my.models.Group;
import com.my.services.lk.LkClient;
import com.my.services.lk.LkParser;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;

import javax.crypto.NoSuchPaddingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import static java.util.Collections.emptyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Переменные окружения не нужны
@Disabled
class PlannedSubjectsUpdateTest {

    @InjectMocks
    PlannedSubjectsUpdate plannedSubjectsUpdate;
    {
        try {
            plannedSubjectsUpdate = new PlannedSubjectsUpdate();
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
        }
    }

    @Mock
    LkClient lkClient;

    @Spy
    Main main = new Main();

    @Test
    void sameSemesterUpdate_emptySubjectsFromLk_thenWithoutNewInfoReport()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        Group group = new Group().setSubjects(TestUtils.createSubjects());

        LkParser lkParser = Mockito.spy(LkParser.class);
        group.setLkParser(lkParser);

        when(lkParser.getNewSubjects(any(), any())).thenReturn(emptyList());

        Method sameSemesterUpdate = PlannedSubjectsUpdate.class
                .getDeclaredMethod("sameSemesterUpdate", Group.class);
        sameSemesterUpdate.setAccessible(true);

        sameSemesterUpdate.invoke(plannedSubjectsUpdate, group);

        verify(plannedSubjectsUpdate).start();
    }

}