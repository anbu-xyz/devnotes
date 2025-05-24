<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Pomodoro Timer</title>
    <link rel="stylesheet" href="css/pomodoro.css">
</head>

<body>
<div class="main">
    <div class="timer-circle" id="timer">25:00</div>
    <div class="control-buttons">
        <button onclick="togglePauseResume()">
            Pause
        </button>
        <button onclick="restartTimer()">
            Restart
        </button>
        <button onclick="chooseTime()">
            Choose Time
        </button>
    </div>
</div>
<script src="js/pomodoro.js"></script>
</body>

</html>