package io.sonarswift.plugin.rules;

import java.util.List;

public final class ObjectiveCCheckList {

    private ObjectiveCCheckList() {}

    public static final List<String> KEYS = List.of(
            "S2001", // long method
            "S2002", // long class
            "S2003", // magic number
            "S2500", // NSNumber compared with ==
            "S2501", // retain cycle in block
            "S2800"  // weak crypto: MD5 (ObjC variant)
            // ... more keys added incrementally
    );

    public static final List<Class<?>> CHECK_CLASSES = List.of(
            // Filled as ObjC checks are implemented (see rules.objc package).
    );
}
