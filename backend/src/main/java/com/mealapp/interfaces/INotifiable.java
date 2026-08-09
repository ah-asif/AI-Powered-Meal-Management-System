package com.mealapp.interfaces;

import java.sql.SQLException;

/**
 * INotifiable
 * -----------
 * Contract for anything that can receive a system notification.
 * Implemented by Student (see model/Student.java).
 */
public interface INotifiable {
    void sendNotification(String message) throws SQLException;
}
