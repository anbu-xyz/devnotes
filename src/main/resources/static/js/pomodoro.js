let timer;
let minutes = 25;
let seconds = 0;
let isPaused = true;
let enteredTime = null;
let state = 'NOT_STARTED';
let startedAtUtc = null;
let overallDuration = 0;

function initializeTimer(config) {
    minutes = config.minutes;
    seconds = config.seconds;
    state = config.state;
    isPaused = state !== 'RUNNING';
    startedAtUtc = config.startedAtUtc;
    overallDuration = config.overallDuration;

    if (state === 'RUNNING') {
        startTimer();
    }
}

function startTimer() {
    timer = setInterval(updateTimer, 1000);
}

function updateTimer() {
    const timerElement = document.getElementById('timer');
    const displayTime = formatTime(minutes, seconds);
    document.title = "Pomodoro " + displayTime;
    timerElement.textContent = displayTime;

    if (minutes === 0 && seconds === 0) {
        clearInterval(timer);
        alert('Time is up! Take a break.');
        updateServerState('STOPPED');
    } else if (!isPaused) {
        if (seconds > 0) {
            seconds--;
        } else {
            seconds = 59;
            minutes--;
        }
        updateServerState('RUNNING');
    }
}

function updateServerState(newState) {
    const config = {
        startedAtUtc: startedAtUtc,
        timeLeftInSeconds: minutes * 60 + seconds,
        overallDurationInSeconds: overallDuration,
        state: newState
    };

    fetch('/pomodoro', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(config)
    }).then(r => r.text().then(data => console.log(data)));
}

function formatTime(minutes, seconds) {
    return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

function togglePauseResume() {
    const pauseResumeButton = document.querySelector('#pauseResumeButton');
    isPaused = !isPaused;

    if (isPaused) {
        clearInterval(timer);
        pauseResumeButton.textContent = 'Resume';
        updateServerState('PAUSED');
    } else {
        startTimer();
        pauseResumeButton.textContent = 'Pause';
        updateServerState('RUNNING');
    }
}

function restartTimer() {
    clearInterval(timer);
    minutes = enteredTime || Math.floor(overallDuration / 60);
    seconds = 0;
    isPaused = false;

    const timerElement = document.getElementById('timer');
    const displayTime = formatTime(minutes, seconds);
    document.title = "Pomodoro " + displayTime;
    timerElement.textContent = displayTime;

    const pauseResumeButton = document.querySelector('#pauseResumeButton');
    pauseResumeButton.textContent = 'Pause';

    startedAtUtc = new Date().toISOString();
    updateServerState('RUNNING');
    startTimer();
}

function chooseTime() {
    const newTime = prompt('Enter new time in minutes:');
    if (!isNaN(newTime) && newTime > 0) {
        enteredTime = parseInt(newTime);
        minutes = enteredTime;
        seconds = 0;
        isPaused = false;
        overallDuration = minutes * 60;

        const timerElement = document.getElementById('timer');
        timerElement.textContent = formatTime(minutes, seconds);

        clearInterval(timer);
        const pauseResumeButton = document.querySelector('#pauseResumeButton');
        pauseResumeButton.textContent = 'Pause';

        startedAtUtc = new Date().toISOString();
        updateServerState('RUNNING');
        startTimer();
    } else {
        alert('Invalid input. Please enter a valid number greater than 0.');
    }
}