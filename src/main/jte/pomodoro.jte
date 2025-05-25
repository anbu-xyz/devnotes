@import uk.anbu.devnotes.service.PomodoroService.PomodoroConfig
@import uk.anbu.devnotes.service.PomodoroService.PomodoroState
@param PomodoroConfig pomodoroConfig

<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Pomodoro ${pomodoroConfig.timeLeftInSeconds() / 60}</title>
    <link rel="stylesheet" href="css/pomodoro.css">
</head>

<body>
<div class="main">
    <div class="timer-circle" id="timer">${String.format("%02d:%02d", pomodoroConfig.timeLeftInSeconds() / 60, pomodoroConfig.timeLeftInSeconds() % 60)}</div>
    <div class="control-buttons">
        <button onclick="togglePauseResume()" id="pauseResumeButton">
            ${pomodoroConfig.state() == PomodoroState.RUNNING ? "Pause" : "Resume"}
        </button>
        <button onclick="chooseTime()">
            Choose Time
        </button>
    </div>
</div>
<script src="js/pomodoro.js"></script>
<script>
    initializeTimer({
        minutes: ${pomodoroConfig.timeLeftInSeconds() / 60},
        seconds: ${pomodoroConfig.timeLeftInSeconds() % 60},
        state: "${pomodoroConfig.state()}",
        overallDuration: ${pomodoroConfig.overallDurationInSeconds()},
        startedAtUtc: "${String.valueOf(pomodoroConfig.startedAtUtc())}"
    });
</script>
</body>

</html>