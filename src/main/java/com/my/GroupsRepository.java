package com.my;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Projections;
import com.my.models.*;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.empty;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Projections.*;
import static com.mongodb.client.model.Updates.*;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class GroupsRepository {

    public static final String _ID = "_id";
    public static final String NAME = "name";
    public static final String USERS = "users";
    public static final String USERS_TO_VERIFY = "usersToVerify";
    public static final String LOGIN_WAITING_USERS = "loginWaitingUsers";
    public static final String LOGGED_USER = "loggedUser";
    public static final String SUBJECTS = "subjects";
    public static final String TIMETABLE = "timetable";
    public static final String LAST_CHECK_DATE = "lastCheckDate";
    public static final String LK_SEMESTER_ID = "lkSemesterId";
    public static final String SCHEDULE_SENT = "scheduleSent";
    public static final String UPDATE_INTERVAL = "updateInterval";
    public static final String UPDATE_AUTH_DATA_NOTIFIED = "loggedUser.updateAuthDataNotified";

    private static GroupsRepository instance = null;
    public static GroupsRepository getInstance () {
        if (instance == null)
            instance = new GroupsRepository();
        return instance;
    }

    private final MongoClient mongoClient;
    private final MongoCollection<Group> groupsCollection;

    private GroupsRepository () {
        final CodecRegistry pojoCodecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().automatic(true).build()));

        final ConnectionString connectionString = new ConnectionString(System.getenv("MONGO_STRING"));
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .codecRegistry(pojoCodecRegistry)
                .build();

        mongoClient = MongoClients.create(settings);

        groupsCollection = mongoClient
                .getDatabase(connectionString.getDatabase())
                .getCollection("groups", Group.class);
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
                                   @NotEmpty List<Subject> subjects,
                                   @NotNull Date lastCheckDate) {
        updateBy(groupName, combine(
                set(SUBJECTS, subjects),
                set(LAST_CHECK_DATE, lastCheckDate)
        ));
    }

    public void setNewSemesterData (@NotNull String groupName,
                                    @NotEmpty List<Subject> subjects,
                                    @NotNull Date lastCheckDate,
                                    @NotNull Timetable timetable,
                                    @NotBlank String lkSemesterId) {
        updateBy(groupName, combine(
                set(SUBJECTS, subjects),
                set(LAST_CHECK_DATE, lastCheckDate),
                set(TIMETABLE, timetable),
                set(LK_SEMESTER_ID, lkSemesterId)
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

    public void deleteMany (String groupName) {
        groupsCollection.deleteMany(eq(NAME, groupName));
    }

    public void updateUserScheduling(String groupName, Integer userId, boolean isEnable) {
        groupsCollection.updateOne(combine(
                eq(NAME, groupName),
                eq("users._id", userId)),
                set("users.$.everydayScheduleEnabled", isEnable));
    }
}
