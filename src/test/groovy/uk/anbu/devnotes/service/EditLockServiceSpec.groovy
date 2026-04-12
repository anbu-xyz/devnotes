package uk.anbu.devnotes.service

import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class EditLockServiceSpec extends Specification {

    @Subject
    EditLockService service = new EditLockService()

    // -------------------------------------------------------------------------
    // tryAcquireLock - basic cases
    // -------------------------------------------------------------------------

    def "acquiring a lock on an unlocked file succeeds"() {
        when:
        def acquired = service.tryAcquireLock('notes.md', 'token-A', false)

        then:
        acquired
    }

    def "the same token can re-acquire its own lock (heartbeat-style)"() {
        given:
        service.tryAcquireLock('notes.md', 'token-A', false)

        when:
        def acquired = service.tryAcquireLock('notes.md', 'token-A', false)

        then:
        acquired
    }

    def "a different token is denied when a live lock is held"() {
        given:
        service.tryAcquireLock('notes.md', 'token-A', false)

        when:
        def acquired = service.tryAcquireLock('notes.md', 'token-B', false)

        then:
        !acquired
    }

    def "acquiring is independent per file"() {
        given:
        service.tryAcquireLock('alpha.md', 'token-A', false)

        when:
        def acquired = service.tryAcquireLock('beta.md', 'token-B', false)

        then:
        acquired
    }

    // -------------------------------------------------------------------------
    // force release
    // -------------------------------------------------------------------------

    def "force=true allows a different token to take over a live lock"() {
        given:
        service.tryAcquireLock('notes.md', 'token-A', false)

        when:
        def acquired = service.tryAcquireLock('notes.md', 'token-B', true)

        then:
        acquired
    }

    def "after a force takeover the original token can no longer heartbeat"() {
        given:
        service.tryAcquireLock('notes.md', 'token-A', false)
        service.tryAcquireLock('notes.md', 'token-B', true)

        when:
        def alive = service.heartbeat('notes.md', 'token-A')

        then:
        !alive
    }

    // -------------------------------------------------------------------------
    // heartbeat
    // -------------------------------------------------------------------------

    def "heartbeat is accepted for the lock owner"() {
        given:
        service.tryAcquireLock('notes.md', 'token-A', false)

        when:
        def alive = service.heartbeat('notes.md', 'token-A')

        then:
        alive
    }

    def "heartbeat is rejected for a non-owner token"() {
        given:
        service.tryAcquireLock('notes.md', 'token-A', false)

        when:
        def alive = service.heartbeat('notes.md', 'token-B')

        then:
        !alive
    }

    def "heartbeat on an unlocked file returns false"() {
        when:
        def alive = service.heartbeat('notes.md', 'token-X')

        then:
        !alive
    }

    // -------------------------------------------------------------------------
    // releaseLock
    // -------------------------------------------------------------------------

    def "releasing a lock allows another token to acquire it"() {
        given:
        service.tryAcquireLock('notes.md', 'token-A', false)

        when:
        service.releaseLock('notes.md', 'token-A')
        def acquired = service.tryAcquireLock('notes.md', 'token-B', false)

        then:
        acquired
    }

    def "releasing with a wrong token does not remove the lock"() {
        given:
        service.tryAcquireLock('notes.md', 'token-A', false)

        when:
        service.releaseLock('notes.md', 'token-X')
        def acquired = service.tryAcquireLock('notes.md', 'token-B', false)

        then:
        !acquired
    }

    def "releasing a non-existent lock is a no-op"() {
        when:
        service.releaseLock('notes.md', 'token-X')

        then:
        noExceptionThrown()
    }

    // -------------------------------------------------------------------------
    // lock expiry
    // -------------------------------------------------------------------------

    def "an expired lock is automatically released on the next acquire attempt"() {
        given: "inject a lock with a heartbeat in the distant past"
        def expiredLock = new EditLockService.EditLock('token-A',
                Instant.now().minus(EditLockService.LOCK_TIMEOUT).minusSeconds(1))
        service.locks.put('notes.md', expiredLock)

        when:
        def acquired = service.tryAcquireLock('notes.md', 'token-B', false)

        then:
        acquired
    }

    def "an expired lock is also auto-released on a heartbeat call"() {
        given:
        def expiredLock = new EditLockService.EditLock('token-A',
                Instant.now().minus(EditLockService.LOCK_TIMEOUT).minusSeconds(1))
        service.locks.put('notes.md', expiredLock)

        when:
        def alive = service.heartbeat('notes.md', 'token-A')

        then:
        !alive
    }
}