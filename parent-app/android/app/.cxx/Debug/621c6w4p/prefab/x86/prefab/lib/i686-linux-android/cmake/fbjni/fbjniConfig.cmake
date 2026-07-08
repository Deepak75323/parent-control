if(NOT TARGET fbjni::fbjni)
add_library(fbjni::fbjni SHARED IMPORTED)
set_target_properties(fbjni::fbjni PROPERTIES
    IMPORTED_LOCATION "/private/var/folders/g4/_7cvdlvs0nd_31c4dgz65dpr0000gn/T/cursor-sandbox-cache/43477ead03bd91f30e48c92b6ddf4e58/gradle/caches/8.10.2/transforms/8b60b4f75564ac53567672df7a1c9a73/transformed/fbjni-0.6.0/prefab/modules/fbjni/libs/android.x86/libfbjni.so"
    INTERFACE_INCLUDE_DIRECTORIES "/private/var/folders/g4/_7cvdlvs0nd_31c4dgz65dpr0000gn/T/cursor-sandbox-cache/43477ead03bd91f30e48c92b6ddf4e58/gradle/caches/8.10.2/transforms/8b60b4f75564ac53567672df7a1c9a73/transformed/fbjni-0.6.0/prefab/modules/fbjni/include"
    INTERFACE_LINK_LIBRARIES ""
)
endif()

