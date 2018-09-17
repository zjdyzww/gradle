defaultTasks("clean", "run")

task("clean") {
    doLast {
        println("Default Cleaning!")
    }
}

task("run") {
    doLast {
        println("Default Running!")
    }
}

task("other") {
    doLast {
        println("I'm not a default task!")
    }
}
