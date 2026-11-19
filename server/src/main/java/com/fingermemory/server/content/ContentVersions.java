package com.fingermemory.server.content;

public final class ContentVersions {
    private ContentVersions() {
    }

    public static int compare(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        if (leftParts.length != 3 || rightParts.length != 3) {
            throw new IllegalArgumentException("版本必须使用 x.y.z 格式");
        }
        for (int index = 0; index < 3; index += 1) {
            int comparison = Integer.compare(
                    Integer.parseInt(leftParts[index]),
                    Integer.parseInt(rightParts[index]));
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }
}
