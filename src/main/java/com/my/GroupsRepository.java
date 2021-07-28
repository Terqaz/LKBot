package com.my;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.lang.Nullable;
import com.my.models.Group;
import com.my.models.SubjectData;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.ne;
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

    private final CodecRegistry pojoCodecRegistry;
    private final MongoClient mongoClient;
    private final MongoCollection<Group> groupsCollection;


    private GroupsRepository () {
        pojoCodecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().automatic(true).build()));

        mongoClient = new MongoClient("localhost", 27017);

        groupsCollection = mongoClient
                .getDatabase("lk-bot").withCodecRegistry(pojoCodecRegistry)
                .getCollection("groups", Group.class);
    }


    public void initialInsert (Group group) {
        if (groupsCollection.countDocuments(eq("name", group.getName())) == 0)
            groupsCollection.insertOne(group);
    }

    public FindIterable<Group> findAllLogged () {
        return groupsCollection.find(ne("lastCheckDate", null));
    }

    public FindIterable<Group> findAllUsersOfGroups () {
        return groupsCollection.find().projection(
                fields(include("name", "loggedUserId", "users", "loginWaitingUsers"),
                        excludeId()));
    }

    public Optional<Group> findByGroupName (String groupName) {
        return Optional.ofNullable(groupsCollection.find(eq("name", groupName)).first());
    }

    public void updateAuthInfo(@NotNull String groupName,
                               @Nullable Integer loggedUsedId,
                               @Nullable String login,
                               @Nullable String password) {
        groupsCollection.updateOne(eq("name", groupName),
                combine(set("loggedUsedId", loggedUsedId),
                        set("login", login),
                        set("password", password),
                        pull("users", loggedUsedId)));
    }

    public void updateSubjectsData (@NotNull String groupName,
                                    @NotEmpty List<SubjectData> subjectsData,
                                    @NotNull Date lastCheckDate) {
        groupsCollection.updateOne(eq("name", groupName),
                combine(set("subjectsData", subjectsData),
                        set("lastCheckDate", lastCheckDate)));
    }

    public void updateAuthInfoAndSubjectsData (Group group, String oldName) {
        groupsCollection.updateOne(eq("name", oldName),
                combine(set("name", group.getName()), // Если в лк другое название группы
                        set("loggedUsedId", group.getLoggedUserId()),
                        set("login", group.getLogin()),
                        set("password", group.getPassword()),
                        set("subjectsData", group.getSubjectsData()),
                        set("lastCheckDate", group.getLastCheckDate())
                ));
    }

    public <T> void updateField (String groupName, String fieldName, T value) {
        groupsCollection.updateOne(eq("name", groupName),
                set(fieldName, value));
    }

    public void addUserTo (String groupName, String fieldName, Integer userId) {
        groupsCollection.updateOne(eq("name", groupName),
                push(fieldName, userId));
    }

    public void moveLoginWaitingUsersToUsers (String groupName) {
        final var loginWaitingUsers = groupsCollection.find(eq("name", groupName))
                .projection(fields(include("loginWaitingUsers"), excludeId()));

        groupsCollection.updateOne(eq("name", groupName),
                pushEach("users", loginWaitingUsers.first().getUsers()));
    }

    public List<Integer> findGroupUsers (String groupName) {
        return Optional.ofNullable(groupsCollection.find(eq("name", groupName))
                .projection(fields(include("users"), excludeId()))
                .first())
                .map(Group::getUsers)
                .orElseGet(Collections::emptyList);
    }
}
