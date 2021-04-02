package util

/**
 * This object maps users to unique id.
 *
 */
class UserMapper : Mapper(ProjectConfig.USER_ID, ProjectConfig.ID_USER) {
    val userToId : Map<String, Int>
        get() = entityToId

    val idToUser : Map<Int, String>
        get() = idToEntity
}
