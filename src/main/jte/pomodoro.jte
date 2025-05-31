@import uk.anbu.devnotes.service.PomodoroService.PomodoroConfig
@import uk.anbu.devnotes.service.PomodoroService.PomodoroState
@param PomodoroConfig pomodoroConfig

<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Pomodoro ${pomodoroConfig.timeLeftInSeconds() / 60}:${pomodoroConfig.timeLeftInSeconds() % 60}</title>
    <link rel="stylesheet" href="css/pomodoro.css">
</head>

<body>
<div class="main">
    <div class="timer-circle" id="timer">${String.format("%02d:%02d", pomodoroConfig.timeLeftInSeconds() / 60, pomodoroConfig.timeLeftInSeconds() % 60)}</div>
    <div class="control-buttons">
        <button onclick="pomodoro.toggleTimer()" id="pauseResumeButton">
            ${pomodoroConfig.state() == PomodoroState.RUNNING ? "Pause" : "Resume"}
        </button>
        <button onclick="pomodoro.chooseTime()" id="chooseTimeButton">
            Choose Time
        </button>
    </div>
</div>
<script src="js/pomodoro.js"></script>
<script>
    pomodoro.init({
        minutes: ${pomodoroConfig.timeLeftInSeconds() / 60},
        seconds: ${pomodoroConfig.timeLeftInSeconds() % 60},
        state: "${pomodoroConfig.state()}",
        timeLeftInSeconds: ${pomodoroConfig.timeLeftInSeconds()},
        updateTimestamp: "${String.valueOf(pomodoroConfig.updateTimestamp())}"
    });
</script>
<div id="timerCompleteModal" class="modal">
    <div class="modal-content">
        <h2>Time's Up!</h2>
        <p>Your pomodoro session is complete.</p>
        <button onclick="pomodoro.closeModal()" class="btn-blue-glow">Start New Session</button>
    </div>
</div>

</body>

</html>