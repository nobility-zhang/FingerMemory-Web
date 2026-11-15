package com.fingermemory.server.learning;

import tools.jackson.databind.node.ObjectNode;

public record LockedUserData(UserDataRow row, ObjectNode document) {
}
