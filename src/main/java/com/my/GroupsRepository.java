package com.my;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.my.models.Group;
import com.my.models.LoggedUser;
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


    public void insert (Group group) {
        groupsCollection.insertOne(group);
    }

    public FindIterable<Group> findAll () {
        return groupsCollection.find();
    }

    public FindIterable<Group> findAllUsersOfGroups () {
        return groupsCollection.find().projection(
                fields(include("name", "users", "loginWaitingUsers"),
                        excludeId()));
    }

    public Optional<Group> findByGroupName (String groupName) {
        return Optional.ofNullable(groupsCollection.find(eq("name", groupName)).first());
    }

    public void updateAuthInfo(@NotNull String groupName,
                               LoggedUser loggedUser) {
        groupsCollection.updateOne(eq("name", groupName),
                combine(set("loggedUsed", loggedUser),
                        pull("users", loggedUser.getId())));
    }

    public void updateSubjectsData (@NotNull String groupName,
                                    @NotEmpty List<SubjectData> subjectsData,
                                    @NotNull Date lastCheckDate) {
        groupsCollection.updateOne(eq("name", groupName),
                combine(set("subjectsData", subjectsData),
                        set("lastCheckDate", lastCheckDate)));
    }

    public <T> void updateField (String groupName, String fieldName, T value) {
        groupsCollection.updateOne(eq("name", groupName),
                set(fieldName, value));
    }

    public void addUserTo (String groupName, String fieldName, Integer userId) {
        groupsCollection.updateOne(eq("name", groupName),
                addToSet(fieldName, userId));
    }

    public void removeLoggedUser(String groupName, Integer loggedUserId) {
        updateAuthInfo(groupName, null);
        removeUserFromGroup(groupName, loggedUserId);
    }

    public void removeUserFromGroup (String groupName, Integer userId) {
        groupsCollection.updateOne(eq("name", groupName),
                pull("users", userId));
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

    public void updateSilentMode (String groupName, int silentModeStart, int silentModeEnd) {
        groupsCollection.updateOne(eq("name", groupName),
                combine(set("silentModeStart", silentModeStart),
                        set("silentModeEnd", silentModeEnd)));
    }
}
