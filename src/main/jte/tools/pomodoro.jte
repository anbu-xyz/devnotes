@import uk.anbu.devnotes.service.PomodoroService.PomodoroConfig
@import uk.anbu.devnotes.service.PomodoroService.PomodoroState
@param PomodoroConfig pomodoroConfig

<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Pomodoro ${pomodoroConfig.timeLeftInSeconds() / 60}:${pomodoroConfig.timeLeftInSeconds() % 60}</title>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css">
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <link rel="stylesheet" href="/css/pomodoro.css">
</head>

<body>
<div class="main">
    <div class="timer-circle" id="timer">${String.format("%02d:%02d", pomodoroConfig.timeLeftInSeconds() / 60, pomodoroConfig.timeLeftInSeconds() % 60)}</div>
    <div class="control-buttons">
        <button onclick="pomodoro.toggleTimer()" id="pauseResumeButton" class="btn-blue-glow">
            <i class="fas ${pomodoroConfig.state() == PomodoroState.RUNNING ? "fa-pause" : "fa-play"}"
               id="pauseResumeIcon"></i>
            <span id="pauseResumeText">${pomodoroConfig.state() == PomodoroState.RUNNING ? "Pause" : "Resume"}</span>
        </button>
        <button onclick="pomodoro.chooseTime()" id="chooseTimeButton" class="btn-blue-glow">
            <i class="fas fa-clock"></i>Choose Time
        </button>
    </div>
</div>
<script src="/js/pomodoro.js"></script>
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
        <button onclick="pomodoro.addFive()" class="btn-blue-glow">
            <i class="fas fa-plus-circle"></i>Add five minutes
        </button>
        <button onclick="pomodoro.closeModal()" class="btn-blue-glow">
            <i class="fas fa-times"></i>Close
        </button>
    </div>
</div>

</body>

</html>