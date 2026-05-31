package cz.kula.killteamdiscordbot.weeklyattendancepoll;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

@Getter
@RequiredArgsConstructor
public enum AttendanceOption {

    YES("Yes", 1),
    NO("No", 2),
    YES_LEARNING("Yes and I want a tutored game", 3),
    YES_TEACHING("Yes and I can teach", 4),
    ;

    private final String label;
    private final int index;

    public static List<String> labels() {
        return Arrays.stream(values()).map(AttendanceOption::getLabel).toList();
    }
}
