package com.fingermemory.server.learning;

import com.fingermemory.server.common.JsonSupport;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class UserDataStore {
    private final UserDataMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public UserDataStore(UserDataMapper mapper, ObjectMapper objectMapper, Clock clock) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public UserDataRow read(String ownerId) {
        UserDataRow row = mapper.find(ownerId);
        if (row == null) {
            ensure(ownerId);
            row = mapper.find(ownerId);
        }
        if (row == null) {
            throw new IllegalStateException("User data row was not created");
        }
        return row;
    }

    public void initialize(String ownerId) {
        ensure(ownerId);
    }

    public ObjectNode readDocument(String ownerId) {
        return readNode(read(ownerId));
    }

    @Transactional
    public LockedUserData lock(String ownerId) {
        UserDataRow row = mapper.findForUpdate(ownerId);
        if (row == null) {
            ensure(ownerId);
            row = mapper.findForUpdate(ownerId);
        }
        if (row == null) {
            throw new IllegalStateException("Locked user data row is missing");
        }
        return new LockedUserData(row, readNode(row));
    }

    public void save(LockedUserData locked, ObjectNode document) {
        long nextVersion = locked.row().getDataVersion() + 1;
        int updated = mapper.update(
                locked.row().getOwnerId(),
                locked.row().getDataVersion(),
                nextVersion,
                objectMapper.writeValueAsString(document),
                clock.instant().toEpochMilli());
        if (updated != 1) {
            throw new IllegalStateException("User data version changed while row was locked");
        }
        locked.row().setDataVersion(nextVersion);
        locked.row().setPayloadJson(objectMapper.writeValueAsString(document));
    }

    private void ensure(String ownerId) {
        mapper.insertDefault(
                ownerId,
                objectMapper.writeValueAsString(JsonSupport.emptyUserData(objectMapper)),
                clock.instant().toEpochMilli());
    }

    private ObjectNode readNode(UserDataRow row) {
        if (row.getSchemaVersion() != 4 || row.getDataFormatVersion() != 4) {
            throw new IllegalStateException("Unsupported stored user data format");
        }
        return JsonSupport.copyObject(objectMapper.readTree(row.getPayloadJson()), "user data");
    }
}
