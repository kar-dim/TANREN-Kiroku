package gr.dkaratzas.tanrenkiroku.data

// keep in sync with TANREN Metsuke, exerciseId values must match exactly
data class Exercise(val id: String, val name: String)
data class MuscleGroup(val name: String, val exercises: List<Exercise>)

// default available exercises
val EXERCISE_CATALOG: List<MuscleGroup> = listOf(

    MuscleGroup(
        "Chest", listOf(
            Exercise("bench_press", "Flat Barbell Bench Press"),
            Exercise("flat_dumbbell_bench_press", "Flat Dumbbell Bench Press"),
            Exercise("incline_barbell_bench_press", "Incline Barbell Bench Press"),
            Exercise("incline_dumbbell_bench_press", "Incline Dumbbell Bench Press"),
            Exercise("decline_barbell_bench_press", "Decline Barbell Bench Press"),
            Exercise("decline_dumbbell_bench_press", "Decline Dumbbell Bench Press"),
            Exercise("smith_machine_bench_press", "Smith Machine Bench Press"),
            Exercise("smith_machine_incline_bench", "Smith Machine Incline Bench Press"),
            Exercise("flat_dumbbell_fly", "Flat Dumbbell Fly"),
            Exercise("incline_dumbbell_fly", "Incline Dumbbell Fly"),
            Exercise("decline_dumbbell_fly", "Decline Dumbbell Fly"),
            Exercise("cable_crossover", "Cable Crossover"),
            Exercise("cable_crossover_low_to_high", "Cable Crossover Low to High"),
            Exercise("cable_fly_high_to_low", "Cable Fly High to Low"),
            Exercise("cable_chest_press", "Cable Chest Press"),
            Exercise("seated_cable_chest_fly", "Seated Cable Chest Fly"),
            Exercise("machine_press", "Machine Press"),
            Exercise("seated_machine_fly", "Seated Machine Fly"),
            Exercise("floor_press", "Floor Press"),
            Exercise("dumbbell_floor_press", "Dumbbell Floor Press"),
            Exercise("push_up", "Push Up"),
            Exercise("weighted_push_up", "Weighted Push Up"),
        )
    ),
    MuscleGroup(
        "Shoulders", listOf(
            Exercise("overhead_press", "Overhead Press"),
            Exercise("seated_dumbbell_press", "Seated Dumbbell Press"),
            Exercise("arnold_press", "Arnold Press"),
            Exercise("machine_shoulder_press", "Machine Shoulder Press"),
            Exercise("push_press", "Push Press"),
            Exercise("behind_neck_press", "Behind-the-Neck Press"),
            Exercise("landmine_press", "Landmine Press"),
            Exercise("lateral_raise", "Lateral Raise"),
            Exercise("cable_lateral_raise", "Cable Lateral Raise"),
            Exercise("machine_lateral_raise", "Machine Lateral Raise"),
            Exercise("dumbbell_front_raise", "Dumbbell Front Raise"),
            Exercise("cable_front_raise", "Cable Front Raise"),
            Exercise("barbell_front_raise", "Barbell Front Raise"),
            Exercise("rear_delt_fly", "Rear Delt Fly"),
            Exercise("reverse_machine_fly", "Reverse Machine Fly"),
            Exercise("barbell_rear_delt_row", "Barbell Rear Delt Row"),
            Exercise("cable_rear_delt_row", "Cable Rear Delt Row"),
            Exercise("face_pull", "Face Pull"),
        )
    ),
    MuscleGroup(
        "Triceps", listOf(
            Exercise("tricep_pushdown", "Tricep Pushdown"),
            Exercise("rope_pushdown", "Rope Pushdown"),
            Exercise("single_arm_pushdown", "Single-Arm Pushdown"),
            Exercise("close_grip_bench_press", "Close Grip Bench Press"),
            Exercise("dip", "Dip"),
            Exercise("bench_dip", "Bench Dip"),
            Exercise("dumbbell_overhead_tricep_extension", "Dumbbell Overhead Extension"),
            Exercise("cable_overhead_extension", "Cable Overhead Tricep Extension"),
            Exercise("machine_overhead_extension", "Machine Overhead Triceps Extension"),
            Exercise("ez_bar_skullcrusher", "EZ-Bar Skullcrusher"),
            Exercise("barbell_skullcrusher", "Barbell Skullcrusher"),
            Exercise("dumbbell_skullcrusher", "Dumbbell Skullcrusher"),
            Exercise("dumbbell_kickback", "Dumbbell Kickback"),
            Exercise("crossbody_cable_extension", "Cross-Body Cable Triceps Extension"),
            Exercise("tate_press", "Tate Press"),
            Exercise("diamond_push_up", "Diamond Push Up"),
        )
    ),
    MuscleGroup(
        "Biceps", listOf(
            Exercise("bicep_curl", "Barbell Curl"),
            Exercise("dumbbell_curl", "Dumbbell Curl"),
            Exercise("hammer_curl", "Hammer Curl"),
            Exercise("ez_bar_curl", "EZ-Bar Curl"),
            Exercise("ez_bar_preacher_curl", "EZ-Bar Preacher Curl"),
            Exercise("barbell_preacher_curl", "Barbell Preacher Curl"),
            Exercise("dumbbell_preacher_curl", "Dumbbell Preacher Curl"),
            Exercise("concentration_curl", "Concentration Curl"),
            Exercise("spider_curl", "Spider Curl"),
            Exercise("incline_dumbbell_curl", "Incline Dumbbell Curl"),
            Exercise("drag_curl", "Drag Curl"),
            Exercise("cable_bicep_curl", "Cable Curl"),
            Exercise("cable_curl_rope", "Cable Curl with Rope"),
            Exercise("overhead_cable_curl", "Overhead Cable Curl"),
            Exercise("machine_curl", "Machine Curl"),
            Exercise("cross_body_hammer_curl", "Cross-Body Hammer Curl"),
        )
    ),
    MuscleGroup(
        "Back", listOf(
            Exercise("barbell_row", "Barbell Row"),
            Exercise("dumbbell_row", "Dumbbell Row"),
            Exercise("seated_cable_row", "Seated Cable Row"),
            Exercise("t_bar_row", "T-Bar Row"),
            Exercise("pendlay_row", "Pendlay Row"),
            Exercise("chest_supported_row", "Chest-Supported Row"),
            Exercise("machine_row", "Machine Row"),
            Exercise("gorilla_row", "Gorilla Row"),
            Exercise("inverted_row", "Inverted Row"),
            Exercise("seal_row", "Seal Row"),
            Exercise("renegade_row", "Renegade Row"),
            Exercise("single_arm_cable_row", "Single-Arm Cable Row"),
            Exercise("lat_pulldown", "Lat Pulldown"),
            Exercise("neutral_grip_pulldown", "Neutral Grip Lat Pulldown"),
            Exercise("close_grip_lat_pulldown", "Close-Grip Lat Pulldown"),
            Exercise("pull_up", "Pull Ups"),
            Exercise("chin_up", "Chin-Up"),
            Exercise("straight_arm_pulldown", "Straight Arm Pulldown"),
            Exercise("dumbbell_pullover", "Dumbbell Pullover"),
            Exercise("deadlift", "Deadlift"),
        )
    ),
    MuscleGroup(
        "Traps", listOf(
            Exercise("shrug", "Shrug"),
            Exercise("dumbbell_shrug", "Dumbbell Shrug"),
            Exercise("cable_shrug", "Cable Shrug"),
            Exercise("upright_row", "Upright Row"),
            Exercise("rack_pull", "Rack Pull"),
        )
    ),
    MuscleGroup(
        "Forearms", listOf(
            Exercise("wrist_curl", "Wrist Curl"),
            Exercise("reverse_wrist_curl", "Reverse Wrist Curl"),
            Exercise("reverse_barbell_curl", "Reverse Barbell Curl"),
            Exercise("reverse_dumbbell_curl", "Reverse Dumbbell Curl"),
            Exercise("zottman_curl", "Zottman Curl"),
            Exercise("farmers_carry", "Farmer's Carry"),
            Exercise("dead_hang", "Dead Hang"),
            Exercise("gripper", "Gripper"),
            Exercise("plate_pinch", "Plate Pinch"),
            Exercise("wrist_roller", "Wrist Roller"),
        )
    ),
    MuscleGroup(
        "Core", listOf(
            Exercise("plank", "Plank"),
            Exercise("side_plank", "Side Plank"),
            Exercise("crunch", "Crunch"),
            Exercise("situp", "Situp"),
            Exercise("decline_crunch", "Decline Crunch"),
            Exercise("oblique_crunch", "Oblique Crunch"),
            Exercise("bicycle_crunch", "Bicycle Crunch"),
            Exercise("cable_crunch", "Cable Crunch"),
            Exercise("machine_crunch", "Machine Crunch"),
            Exercise("hanging_leg_raise", "Hanging Leg Raise"),
            Exercise("hanging_knee_raise", "Hanging Knee Raise"),
            Exercise("hanging_windshield_wiper", "Hanging Windshield Wiper"),
            Exercise("captains_chair_leg_raise", "Captain's Chair Leg Raise"),
            Exercise("captains_chair_knee_raise", "Captain's Chair Knee Raise"),
            Exercise("lying_leg_raise", "Lying Leg Raise"),
            Exercise("lying_windshield_wiper", "Lying Windshield Wiper"),
            Exercise("ab_wheel_rollout", "Ab Wheel Rollout"),
            Exercise("dragon_flag", "Dragon Flag"),
            Exercise("russian_twist", "Russian Twist"),
            Exercise("dumbbell_side_bend", "Dumbbell Side Bend"),
            Exercise("pallof_press", "Pallof Press"),
            Exercise("cable_wood_chop", "Cable Wood Chop"),
            Exercise("landmine_rotation", "Landmine Rotation"),
            Exercise("copenhagen_plank", "Copenhagen Plank"),
        )
    ),
    MuscleGroup(
        "Quads", listOf(
            Exercise("squat", "Squat"),
            Exercise("front_squat", "Front Squat"),
            Exercise("goblet_squat", "Goblet Squat"),
            Exercise("hack_squat", "Hack Squat"),
            Exercise("barbell_hack_squat", "Barbell Hack Squat"),
            Exercise("pendulum_squat", "Pendulum Squat"),
            Exercise("safety_bar_squat", "Safety Bar Squat"),
            Exercise("box_squat", "Box Squat"),
            Exercise("belt_squat", "Belt Squat"),
            Exercise("smith_machine_squat", "Smith Machine Squat"),
            Exercise("cyclist_squat", "Cyclist Squat"),
            Exercise("sissy_squat", "Sissy Squat"),
            Exercise("leg_press", "Leg Press"),
            Exercise("leg_extension", "Leg Extension"),
            Exercise("trap_bar_deadlift", "Trap Bar Deadlift"),
            Exercise("bulgarian_split_squat", "Bulgarian Split Squat"),
            Exercise("lunge", "Lunge"),
            Exercise("walking_lunge", "Walking Lunge"),
            Exercise("reverse_lunge", "Reverse Lunge"),
            Exercise("lateral_lunge", "Lateral Lunge"),
            Exercise("curtsy_lunge", "Curtsy Lunge"),
            Exercise("step_up", "Step Up"),
            Exercise("pistol_squat", "Pistol Squat"),
            Exercise("reverse_nordic", "Reverse Nordic"),
            Exercise("hip_adduction_machine", "Hip Adduction Machine"),
        )
    ),
    MuscleGroup(
        "Hamstrings", listOf(
            Exercise("leg_curl", "Leg Curl"),
            Exercise("seated_leg_curl", "Seated Leg Curl"),
            Exercise("romanian_deadlift", "Romanian Deadlift"),
            Exercise("romanian_dumbbell_deadlift", "Romanian Dumbbell Deadlift"),
            Exercise("stiff_leg_deadlift", "Stiff-Leg Deadlift"),
            Exercise("sumo_deadlift", "Sumo Deadlift"),
            Exercise("single_leg_rdl", "Single-Leg Romanian Deadlift"),
            Exercise("nordic_curl", "Nordic Curl"),
            Exercise("glute_ham_raise", "Glute-Ham Raise"),
            Exercise("good_morning", "Good Morning"),
            Exercise("back_extension", "Back Extension"),
        )
    ),
    MuscleGroup(
        "Glutes", listOf(
            Exercise("glute_bridge", "Glute Bridge"),
            Exercise("single_leg_glute_bridge", "Single-Leg Glute Bridge"),
            Exercise("barbell_hip_thrust", "Barbell Hip Thrust"),
            Exercise("dumbbell_hip_thrust", "Dumbbell Hip Thrust"),
            Exercise("single_leg_hip_thrust", "Single-Leg Hip Thrust"),
            Exercise("hip_thrust_machine", "Hip Thrust Machine"),
            Exercise("cable_glute_kickback", "Cable Glute Kickback"),
            Exercise("donkey_kickback", "Donkey Kickback"),
            Exercise("cable_pull_through", "Cable Pull-Through"),
            Exercise("reverse_hyperextension", "Reverse Hyperextension"),
            Exercise("kettlebell_swing", "Kettlebell Swing"),
            Exercise("sumo_squat", "Sumo Squat"),
            Exercise("cossack_squat", "Cossack Squat"),
            Exercise("hip_abduction_machine", "Hip Abduction Machine"),
            Exercise("lateral_band_walk", "Lateral Band Walk"),
            Exercise("frog_pump", "Frog Pump"),
        )
    ),
    MuscleGroup(
        "Calves", listOf(
            Exercise("calf_raise", "Calf Raise"),
            Exercise("seated_calf_raise", "Seated Calf Raise"),
            Exercise("single_leg_calf_raise", "Single-Leg Calf Raise"),
            Exercise("single_leg_seated_calf_raise", "Single-Leg Seated Calf Raise"),
            Exercise("donkey_calf_raise", "Donkey Calf Raise"),
            Exercise("leg_press_calf_raise", "Leg Press Calf Raise"),
            Exercise("barbell_calf_raise", "Barbell Calf Raise"),
        )
    ),
    MuscleGroup(
        "Shins", listOf(
            Exercise("tibialis_wall_raise", "Tibialis Wall Raise"),
            Exercise("tib_bar_raise", "Tib-Bar Raise"),
        )
    ),
)

private val exerciseNameMap: Map<String, String> = EXERCISE_CATALOG.flatMap { it.exercises }.associate { it.id to it.name }
fun exerciseDisplayName(id: String): String = exerciseNameMap[id] ?: id
