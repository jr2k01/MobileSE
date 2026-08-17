package com.example.mobilese

/**
 * Kleine Baukaesten fuer die Unit-Tests, damit in den Tests selbst nur das
 * steht, worum es dort geht.
 */
object TestData {

    fun profile(id: String, name: String, email: String = "$id@example.com") =
        UserProfile(id = id, email = email, name = name)

    fun activity(
        userId: String,
        sport: String = Sports.RUNNING,
        duration: Int = 30,
        distance: Double = 0.0,
        intensity: WorkoutIntensity = WorkoutIntensity.HIGH,
        crewId: String = "CRW100",
        timestamp: String = "2026-08-13T18:00:00"
    ) = Activity(
        userId = userId,
        crewId = crewId,
        sport = sport,
        duration = duration,
        distance = distance,
        intensity = intensity.name,
        timestamp = timestamp
    )

    fun challenge(
        id: String = "1",
        type: ChallengeType = ChallengeType.DISTANCE,
        goal: Int = 10,
        reward: Int = 20,
        crewId: String = "CRW100"
    ) = Challenge(id = id, crewId = crewId, type = type.name, goal = goal, reward = reward)

    fun stepDay(userId: String, day: String, steps: Int) =
        StepDay(userId = userId, day = day, steps = steps)

    fun snapshot(
        members: List<UserProfile> = emptyList(),
        activities: List<Activity> = emptyList(),
        challenges: List<Challenge> = emptyList(),
        rewards: List<ChallengeReward> = emptyList(),
        stepDays: List<StepDay> = emptyList()
    ) = CrewSnapshot(members, activities, challenges, rewards, stepDays)
}
