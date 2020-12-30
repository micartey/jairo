package me.clientastisch.micartey.transformer;

import lombok.Getter;

public abstract class Micartey {

    @Getter private final String fieldName;

    public Micartey(String fieldName) {
        this.fieldName = fieldName;
    }

}
