if(NOT TARGET hermes-engine::libhermes)
add_library(hermes-engine::libhermes SHARED IMPORTED)
set_target_properties(hermes-engine::libhermes PROPERTIES
    IMPORTED_LOCATION "/private/var/folders/g4/_7cvdlvs0nd_31c4dgz65dpr0000gn/T/cursor-sandbox-cache/43477ead03bd91f30e48c92b6ddf4e58/gradle/caches/8.10.2/transforms/63e182d7b2029c42e1f28f883e24b322/transformed/hermes-android-0.76.5-debug/prefab/modules/libhermes/libs/android.x86_64/libhermes.so"
    INTERFACE_INCLUDE_DIRECTORIES "/private/var/folders/g4/_7cvdlvs0nd_31c4dgz65dpr0000gn/T/cursor-sandbox-cache/43477ead03bd91f30e48c92b6ddf4e58/gradle/caches/8.10.2/transforms/63e182d7b2029c42e1f28f883e24b322/transformed/hermes-android-0.76.5-debug/prefab/modules/libhermes/include"
    INTERFACE_LINK_LIBRARIES ""
)
endif()

