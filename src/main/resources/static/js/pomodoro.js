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

    const pauseResumeButton = document.querySelector('#pauseResumeButton');
    if (state === 'NOT_STARTED') {
        pauseResumeButton.textContent = 'Start';
    } else if (state === 'RUNNING') {
        pauseResumeButton.textContent = 'Pause';
        startTimer();
    } else if (state === 'PAUSED') {
        pauseResumeButton.textContent = 'Resume';
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
        state = 'NOT_STARTED';
        minutes = 25;
        const pauseResumeButton = document.querySelector('#pauseResumeButton');
        pauseResumeButton.textContent = 'Start';
        updateServerState('NOT_STARTED');
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

    if (state === 'NOT_STARTED') {
        // Call server's start endpoint first
        fetch(`/pomodoro/start?timeLeftInSeconds=${minutes * 60}`, {
            method: 'POST'
        })
            .then(response => response.json())
            .then(data => {
                startedAtUtc = data.startedAtUtc; // Get start time from server
                // Start the timer
                startTimer();
                isPaused = false;
                state = 'RUNNING';
                pauseResumeButton.textContent = 'Pause';
                updateServerState('RUNNING');
            })
            .catch(error => {
                console.error('Error starting timer:', error);
                alert('Failed to start timer');
            });
    } else if (state === 'RUNNING') {
        // Pausing a running timer
        clearInterval(timer);
        isPaused = true;
        state = 'PAUSED';
        pauseResumeButton.textContent = 'Resume';
        updateServerState('PAUSED');
    } else if (state === 'PAUSED') {
        // Resuming a paused timer
        startTimer();
        isPaused = false;
        state = 'RUNNING';
        pauseResumeButton.textContent = 'Pause';
        startedAtUtc = new Date().toISOString();
        updateServerState('RUNNING');
    }
}

function chooseTime() {
    const newTime = prompt('Enter new time in minutes:');
    if (!isNaN(newTime) && newTime > 0) {
        enteredTime = parseInt(newTime);
        minutes = enteredTime;
        seconds = 0;
        isPaused = true;
        state = 'NOT_STARTED';
        overallDuration = minutes * 60;

        const timerElement = document.getElementById('timer');
        timerElement.textContent = formatTime(minutes, seconds);

        clearInterval(timer);
        const pauseResumeButton = document.querySelector('#pauseResumeButton');
        pauseResumeButton.textContent = 'Start';

        updateServerState('NOT_STARTED');
    } else {
        alert('Invalid input. Please enter a valid number greater than 0.');
    }
}