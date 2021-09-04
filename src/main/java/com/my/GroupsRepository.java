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

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Projections.*;
import static com.mongodb.client.model.Updates.*;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class GroupsRepository {

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

    public static final String USERS = "users";
    public static final String NAME = "name";
    private static final String USERS_TO_VERIFY = "usersToVerify";
    private static final String LOGIN_WAITING_USERS = "loginWaitingUsers";
    public static final String LOGGED_USER = "loggedUser";

    public void insert (Group group) {
        groupsCollection.insertOne(group);
    }

    public FindIterable<Group> findAll () {
        return groupsCollection.find();
    }

    public FindIterable<Group> findAllUsersOfGroups () {
        return groupsCollection.find().projection(
                fields(include(NAME, USERS, LOGIN_WAITING_USERS),
                        excludeId()));
    }

    public Optional<Group> findByGroupName (String groupName) {
        return Optional.ofNullable(groupsCollection.find(eq(NAME, groupName)).first());
    }

    public void updateLoggedUser (@NotNull String groupName, @NotNull LoggedUser user) {
        groupsCollection.updateOne(eq("name", groupName),
                combine(set(LOGGED_USER, user),
                        addToSet(USERS, new GroupUser(user.getId()))));
    }

    public void updateSubjectsData (@NotNull String groupName,
                                    @NotEmpty List<SubjectData> subjectsData,
                                    @NotNull Date lastCheckDate) {
        groupsCollection.updateOne(eq(NAME, groupName),
                combine(set("subjectsData", subjectsData),
                        set("lastCheckDate", lastCheckDate)));
    }

    public <T> void updateField (String groupName, String fieldName, T value) {
        groupsCollection.updateOne(eq(NAME, groupName),
                set(fieldName, value));
    }

    public void addToIntegerArray (String groupName, String fieldName, Integer value) {
        groupsCollection.updateOne(eq(NAME, groupName),
                addToSet(fieldName, value));
    }

    public void removeUserFromGroup (String groupName, Integer userId) {
        groupsCollection.updateOne(eq(NAME, groupName),
                combine(removeUserFromGroupOperations(userId)));
    }

    public void removeLoggedUser(String groupName, Integer loggedUserId) {
        final List<Bson> combineOperations = new ArrayList<>();
        combineOperations.add(set(LOGGED_USER, new LoggedUser().setId(0).setAuthData(null)));
        combineOperations.addAll(removeUserFromGroupOperations(loggedUserId));

        groupsCollection.updateOne(eq(NAME, groupName), combine(combineOperations));
    }
    private List<Bson> removeUserFromGroupOperations(Integer userId) {
        return List.of(pull(USERS, eq("_id", userId)),
                       pull(USERS_TO_VERIFY, eq("_id", userId)),
                       pull(LOGIN_WAITING_USERS, userId));
    }

    public void moveLoginWaitingUsersToUsers (String groupName) {
        final Optional<Group> groupOptional = Optional.ofNullable(groupsCollection
                .find(eq(NAME, groupName))
                .projection(fields(
                        include(LOGIN_WAITING_USERS),
                        excludeId()
                )).first());

        groupOptional.ifPresent(group -> groupsCollection.updateOne(
                eq(NAME, groupName),
                combine(addEachToSet(USERS, group.getLoginWaitingUsers().stream()
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
            groupsCollection.updateOne(eq(NAME, groupName),
                    addToSet(USERS_TO_VERIFY, user));
        return count == 0;
    }

    public boolean moveVerifiedUserToUsers (String groupName, UserToVerify user) {
        final Optional<Group> groupOptional = Optional.ofNullable(groupsCollection
                .find(combine(
                        eq(NAME, groupName),
                        eq(USERS_TO_VERIFY, user)))
                .projection(fields(
                        Projections.elemMatch(USERS_TO_VERIFY,
                                combine(eq("_id", user.getId()),
                                        eq("code", user.getCode()))),
                        excludeId()))
                .first());

        if (groupOptional.isPresent()) {
            final UserToVerify user1 = groupOptional.get().getUsersToVerify().get(0);

            groupsCollection.updateOne(
                    eq(NAME, groupName),
                    combine(push(USERS, new GroupUser(user1.getId())),
                            pull(USERS_TO_VERIFY,
                                    combine(eq("_id", user.getId()),
                                            eq("code", user.getCode())))));
            return true;

        } else return false;
    }

    public List<GroupUser> findGroupUsers (String groupName) {
        return Optional.ofNullable(groupsCollection.find(eq(NAME, groupName))
                .projection(fields(include(USERS), excludeId()))
                .first())
                .map(Group::getUsers)
                .orElseGet(Collections::emptyList);
    }

    public void updateSilentMode (String groupName, int silentModeStart, int silentModeEnd) {
        groupsCollection.updateOne(eq(NAME, groupName),
                combine(set("silentModeStart", silentModeStart),
                        set("silentModeEnd", silentModeEnd)));
    }

    public void deleteMany (String groupName) {
        groupsCollection.deleteMany(eq(NAME, groupName));
    }
}
