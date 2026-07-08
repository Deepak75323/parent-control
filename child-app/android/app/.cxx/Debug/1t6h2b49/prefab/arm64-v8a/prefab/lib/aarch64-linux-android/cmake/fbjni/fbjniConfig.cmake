if(NOT TARGET fbjni::fbjni)
add_library(fbjni::fbjni SHARED IMPORTED)
set_target_properties(fbjni::fbjni PROPERTIES
    IMPORTED_LOCATION "/private/var/folders/g4/_7cvdlvs0nd_31c4dgz65dpr0000gn/T/cursor-sandbox-cache/43477ead03bd91f30e48c92b6ddf4e58/gradle/caches/8.10.2/transforms/ee4713c89dc69faa668500deaee892a4/transformed/fbjni-0.6.0/prefab/modules/fbjni/libs/android.arm64-v8a/libfbjni.so"
    INTERFACE_INCLUDE_DIRECTORIES "/private/var/folders/g4/_7cvdlvs0nd_31c4dgz65dpr0000gn/T/cursor-sandbox-cache/43477ead03bd91f30e48c92b6ddf4e58/gradle/caches/8.10.2/transforms/ee4713c89dc69faa668500deaee892a4/transformed/fbjni-0.6.0/prefab/modules/fbjni/include"
    INTERFACE_LINK_LIBRARIES ""
)
endif()

