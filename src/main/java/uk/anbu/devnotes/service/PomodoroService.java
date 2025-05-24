package uk.anbu.devnotes.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PomodoroService {

    private final ConfigService configService;

    public String getDocsDirectory() {
        return configService.getDocsDirectory();
    }

}