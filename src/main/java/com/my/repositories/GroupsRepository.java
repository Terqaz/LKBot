package com.my.repositories;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.my.models.*;
import com.my.models.temp.OldGroup;
import org.bson.conversions.Bson;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.empty;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Projections.*;
import static com.mongodb.client.model.Updates.*;

public class GroupsRepository extends MongoRepository {

    public static final String NAME = "name";
    public static final String USERS = "users";
    public static final String USERS_TO_VERIFY = "usersToVerify";
    public static final String LOGIN_WAITING_USERS = "loginWaitingUsers";
    public static final String LOGGED_USER = "loggedUser";
    public static final String SUBJECTS = "subjects";
    public static final String TIMETABLE = "timetable";
    public static final String SCHEDULE_SENT = "scheduleSent";
    public static final String UPDATE_INTERVAL = "updateInterval";
    public static final String UPDATE_AUTH_DATA_NOTIFIED = "loggedUser.updateAuthDataNotified";
    private static final String MATERIALS_DOCUMENTS = "materialsDocuments";
    private static final String MESSAGES_DOCUMENTS = "messagesDocuments";
    public static final String LK_ID = "lkId";
    public static final String LK_SEMESTER_ID = "lkSemesterId";
    public static final String LK_CONTINGENT_ID = "lkContingentId";

    private static GroupsRepository instance = null;
    public static GroupsRepository getInstance () {
        if (instance == null)
            instance = new GroupsRepository();
        return instance;
    }

    private final MongoCollection<Group> groupsCollection;
    public final MongoCollection<OldGroup> olgGroupsCollection; // TODO temp

    private GroupsRepository () {
        groupsCollection = mongoClient
                .getDatabase(connectionString.getDatabase())
                .getCollection("groups", Group.class);

        olgGroupsCollection = mongoClient
                .getDatabase(connectionString.getDatabase())
                .getCollection("groups", OldGroup.class);
    }

    public void insert (Group group) {
        groupsCollection.insertOne(group);
    }

    public void insertMany (List<Group> group) {
        groupsCollection.insertMany(group);
    }

    public FindIterable<Group> findAllWithoutTimetable () {
        return findAll(fields(
                exclude(TIMETABLE)));
    }

    public FindIterable<Group> findAllWithoutSubjects () {
        return findAll(fields(
                exclude(SUBJECTS)));
    }

    public FindIterable<Group> findAllUsersOfGroups () {
        return findAll(fields(
                include(NAME, USERS, LOGIN_WAITING_USERS),
                excludeId()));
    }

    public FindIterable<Group> findAll() {
        return groupsCollection.find();
    }

    private FindIterable<Group> findAll(Bson projection) {
        return groupsCollection.find().projection(projection);
    }

    public Optional<Group> findByGroupName (String groupName) {
        return Optional.ofNullable(groupsCollection.find(eq(NAME, groupName)).first());
    }

    public void updateLoggedUser (@NotNull String groupName, @NotNull LoggedUser user) {
        updateBy(groupName, combine(
                set(LOGGED_USER, user),
                addToSet(USERS, new GroupUser(user.getId()))));
    }

    public void updateSubjects(@NotNull String groupName,
                               @NotEmpty List<Subject> subjects) {
        updateBy(groupName, combine(
                set(SUBJECTS, subjects)
        ));
    }

    public void updateLkIds(@NotNull String groupName, String lkId, String lkSemesterId, String lkContingentId) {
        updateBy(groupName, combine(
                set(LK_ID, lkId),
                set(LK_SEMESTER_ID, lkSemesterId),
                set(LK_CONTINGENT_ID, lkContingentId)
        ));
    }

    public void setNewSemesterData(@NotNull String groupName,
                                   List<Subject> subjects,
                                   Timetable timetable,
                                   String lkSemesterId, String lkContingentId) {
        updateBy(groupName, combine(
                set(SUBJECTS, subjects),
                set(TIMETABLE, timetable),
                set(LK_SEMESTER_ID, lkSemesterId),
                set(LK_CONTINGENT_ID, lkContingentId)
        ));
    }

    public void addToIntegerArray (String groupName, String fieldName, Integer value) {
        updateBy(groupName, addToSet(fieldName, value));
    }

    public void removeUserFromGroup (String groupName, Integer userId) {
        updateBy(groupName, combine(
                removeUserFromGroupOperations(userId)));
    }

    public void removeLoggedUser(String groupName, Integer loggedUserId) {
        final List<Bson> combineOperations = new ArrayList<>();
        combineOperations.add(set(LOGGED_USER, null));
        combineOperations.addAll(removeUserFromGroupOperations(loggedUserId));

        updateBy(groupName, combine(combineOperations));
    }
    private List<Bson> removeUserFromGroupOperations(Integer userId) {
        return List.of(pull(USERS, eq(_ID, userId)),
                       pull(USERS_TO_VERIFY, eq(_ID, userId)),
                       pull(LOGIN_WAITING_USERS, userId));
    }

//    public void removeDocument(String groupName, Integer subjectId, Integer documentId, boolean isFromMaterials) {
//        final List<Bson> filter = new ArrayList<>();
//        filter.add(eq(SUBJECTS + "." + _ID, subjectId));
//
//        if (isFromMaterials)
//            filter.add(eq(SUBJECTS + "." + MATERIALS_DOCUMENTS+"."+_ID, documentId));
//        else
//            filter.add(eq(SUBJECTS + "." + MESSAGES_DOCUMENTS+"."+_ID, documentId));
//
////        eq("users._id", userId)),
////        set("users.$.everydayScheduleEnabled", isEnable));
//
//        updateBy(groupName, filter,
//                isFromMaterials ?
//                        pull(SUBJECTS + "." + MATERIALS_DOCUMENTS+".$.", eq(_ID, documentId)) :
//                        pull(SUBJECTS + "." + MESSAGES_DOCUMENTS+".$.", eq(_ID, documentId))
//        );
//    }

    public void moveLoginWaitingUsersToUsers (String groupName) {
        final Optional<Group> groupOptional = Optional.ofNullable(groupsCollection
                .find(eq(NAME, groupName))
                .projection(fields(
                        include(LOGIN_WAITING_USERS),
                        excludeId()
                )).first());

        groupOptional.ifPresent(group -> updateBy(groupName, combine(
                        addEachToSet(USERS, group.getLoginWaitingUsers().stream()
                                .map(GroupUser::new)
                                .collect(Collectors.toList())),
                        set(LOGIN_WAITING_USERS, Collections.emptyList()))));
    }

    public boolean addUserToUsersToVerify (String groupName, UserToVerify user) {
        final long count = groupsCollection.countDocuments(combine(
                eq(NAME, groupName),
                eq(USERS_TO_VERIFY+"._id", user.getId())
        ));
        if (count == 0)
            updateBy(groupName, addToSet(USERS_TO_VERIFY, user));
        return count == 0;
    }

    public boolean moveVerifiedUserToUsers (String groupName, UserToVerify user) {
        final Optional<Group> groupOptional = Optional.ofNullable(groupsCollection
                .find(combine(
                        eq(NAME, groupName),
                        eq(USERS_TO_VERIFY, user)))
                .projection(fields(
                        Projections.elemMatch(USERS_TO_VERIFY,
                                combine(eq(_ID, user.getId()),
                                        eq("code", user.getCode()))),
                        excludeId()))
                .first());

        if (groupOptional.isPresent()) {
            final UserToVerify user1 = groupOptional.get().getUsersToVerify().stream().findFirst().get();

            updateBy(groupName, combine(
                    addToSet(USERS, new GroupUser(user1.getId())),
                    pull(USERS_TO_VERIFY,
                            combine(eq(_ID, user.getId()),
                                    eq("code", user.getCode())))));
            return true;

        } else return false;
    }

    public Set<GroupUser> findGroupUsers (String groupName) {
        return Optional.ofNullable(groupsCollection.find(eq(NAME, groupName))
                .projection(fields(include(USERS), excludeId()))
                .first())
                .map(Group::getUsers)
                .orElseGet(Collections::emptySet);
    }

    public void updateSilentMode (String groupName, int silentModeStart, int silentModeEnd) {
        updateBy(groupName, combine(
                set("silentModeStart", silentModeStart),
                set("silentModeEnd", silentModeEnd)));
    }

    public <T> void updateField (String groupName, String fieldName, T value) {
        updateBy(groupName, set(fieldName, value));
    }

    public <T> void updateEachField (String fieldName, T value) {
        groupsCollection.updateMany(empty(), set(fieldName, value));
    }

    private void updateBy(@NotNull String groupName, Bson updateQuery) {
        groupsCollection.updateOne(eq(NAME, groupName), updateQuery);
    }

    private void updateBy(@NotNull String groupName, List<Bson> filter, Bson updateQuery) {
        filter.add(eq(NAME, groupName));
        groupsCollection.updateOne(combine(filter), updateQuery);
    }

    public void deleteMany (String groupName) {
        groupsCollection.deleteMany(eq(NAME, groupName));
    }

    public void updateUserScheduling(String groupName, Integer userId, boolean isEnable) {
        groupsCollection.updateOne(combine(
                eq(NAME, groupName),
                eq("users._id", userId)),
                set("users.$.everydayScheduleEnabled", isEnable));
    }

    public void updateDocumentVkAttachment(String groupName, Integer subjectId, Integer documentId, boolean isFromMaterials, String vkAttachment) {
        if (isFromMaterials) {
            groupsCollection.updateOne(eq(NAME, groupName),
                    set("subjects.$[s].materialsDocuments.$[sd].vkAttachment", vkAttachment),
                    new UpdateOptions().arrayFilters(List.of(
                            eq("s._id", subjectId),
                            eq("sd._id", documentId)))
            );
        } else {
            groupsCollection.updateOne(eq(NAME, groupName),
                    set("subjects.$[s].messagesDocuments.$[sd].vkAttachment", vkAttachment),
                    new UpdateOptions().arrayFilters(List.of(
                            eq("s._id", subjectId),
                            eq("sd._id", documentId)))
            );
        }
    }

    public long updateGroup(@NotNull Group group) {
        return groupsCollection.replaceOne(eq(NAME, group.getName()), group).getModifiedCount();
    }
}
