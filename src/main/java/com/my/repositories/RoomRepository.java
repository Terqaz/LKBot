package com.my.repositories;

import com.mongodb.client.MongoCollection;
import com.my.models.Room;

import static com.mongodb.client.model.Filters.eq;

public class RoomRepository extends MongoRepository {

    private static RoomRepository instance = null;
    public static RoomRepository getInstance () {
        if (instance == null)
            instance = new RoomRepository();
        return instance;
    }

    private final MongoCollection<Room> roomCollection;

    private RoomRepository() {
        super();

        roomCollection = mongoClient
                .getDatabase(connectionString.getDatabase())
                .getCollection("rooms", Room.class);
    }

    public Room findById(String id) {
        return roomCollection.find(eq(_ID, id)).first();
    }
}
