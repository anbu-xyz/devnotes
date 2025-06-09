const pomodoro = {
    timer: null,
    minutes: 25,
    seconds: 0,
    state: 'NOT_STARTED',
    updateTimestamp: null,
    notificationPermission: false,

    init(config) {
        Object.assign(this, config);
        this.updateButtonText();
        if (this.state === 'RUNNING') {
            this.startTimer();
        }
        this.requestNotificationPermission();
    },

    requestNotificationPermission() {
        if ("Notification" in window) {
            Notification.requestPermission().then(permission => {
                this.notificationPermission = permission === "granted";
            });
        }
    },

    showNotification() {
        if (this.notificationPermission) {
            const notification = new Notification("Pomodoro Timer", {
                body: "Time's up! Your pomodoro session is complete.",
                icon: "/favicon.ico"
            });

            notification.onclick = function (event) {
                event.preventDefault(); // Prevent default behavior - focusing the notification

                console.debug('Pomodoro timer notification clicked. Bringing tab to front.');
                window.focus(); // Bring tab to front
            };
        }
    },

    showModal() {
        document.getElementById('timerCompleteModal').style.display = 'block';
    },

    closeModal() {
        document.getElementById('timerCompleteModal').style.display = 'none';
        this.reset(25);
    },

    addFive() {
        document.getElementById('timerCompleteModal').style.display = 'none';
        this.reset(5);
        this.toggleTimer();
    },

    startTimer() {
        this.timer = setInterval(() => this.updateTimer(), 1000);
    },

    updateTimer() {
        if (this.minutes === 0 && this.seconds === 0) {
            this.timerComplete();
            return;
        }

        if (this.state === 'RUNNING') {
            if (this.seconds > 0) {
                this.seconds--;
            } else {
                this.seconds = 59;
                this.minutes--;
            }
            this.updateServerState('RUNNING');
        }

        this.updateDisplay();
    },

    updateDisplay() {
        const displayTime = this.formatTime();
        document.getElementById('timer').textContent = displayTime;
        document.title = `Pomodoro ${displayTime}`;
    },

    formatTime() {
        return `${String(this.minutes).padStart(2, '0')}:${String(this.seconds).padStart(2, '0')}`;
    },

    timerComplete() {
        clearInterval(this.timer);
        this.showModal();
        this.showNotification();
    },

    reset(minutes) {
        this.minutes = minutes;
        this.seconds = 0;
        this.state = 'NOT_STARTED';
        this.updateButtonText();
        this.updateServerState('NOT_STARTED');
    },

    toggleTimer() {
        if (this.state === 'NOT_STARTED') {
            this.startNewTimer();
        } else if (this.state === 'RUNNING') {
            this.pauseTimer();
        } else {
            this.resumeTimer();
        }
    },

    async startNewTimer() {
        try {
            const response = await fetch(`/pomodoro/start?timeLeftInSeconds=${this.minutes * 60}`, {
                method: 'POST'
            });
            const data = await response.json();
            this.updateTimestamp = data.updateTimestamp;
            this.state = 'RUNNING';
            this.startTimer();
            this.updateButtonText();
            this.updateServerState('RUNNING');
        } catch (error) {
            console.error('Error starting timer:', error);
            alert('Failed to start timer');
        }
    },

    pauseTimer() {
        clearInterval(this.timer);
        this.state = 'PAUSED';
        this.updateButtonText();
        this.updateServerState('PAUSED');
    },

    resumeTimer() {
        this.updateTimestamp = new Date().toISOString().split('.')[0];
        this.state = 'RUNNING';
        this.startTimer();
        this.updateButtonText();
        this.updateServerState('RUNNING');
    },

    updateButtonText() {
        const buttonText = {
            'NOT_STARTED': 'Start',
            'RUNNING': 'Pause',
            'PAUSED': 'Resume'
        }[this.state];
        document.querySelector('#pauseResumeButton').textContent = buttonText;
    },

    updateServerState(newState) {
        fetch('/pomodoro', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({
                updateTimestamp: new Date().toISOString().split('.')[0],
                timeLeftInSeconds: this.minutes * 60 + this.seconds,
                state: newState
            })
        }).then(r =>
            r.text().then(data => {
                console.debug(`pomodoro update response: ${data}`);
            })
        );
    },

    chooseTime() {
        const newTime = prompt('Enter new time in minutes:');
        if (!isNaN(newTime) && newTime > 0) {
            this.minutes = parseInt(newTime);
            this.seconds = 0;
            this.state = 'NOT_STARTED';
            clearInterval(this.timer);
            this.updateDisplay();
            this.updateButtonText();
            this.updateServerState('NOT_STARTED');
            this.toggleTimer();
        } else {
            alert('Invalid input. Please enter a valid number greater than 0.');
        }
    }
};