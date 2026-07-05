package de.bugfish.rssreaderadvanced.ui.fragments;

/**
 * A fragment that supports a "mark all as read" action scoped to what the user
 * is currently looking at. Fragments that don't implement this hide the menu
 * item (e.g. the Sources tab).
 */
public interface MarksAllRead {
    void markAllReadVisible();
}
