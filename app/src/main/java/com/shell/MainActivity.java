package com.shell;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class MainActivity extends Activity {

    private TextView outputView;
    private EditText inputView;
    private ScrollView scrollView;

    private Button runButton;

    private volatile Process currentProcess;

    private File currentDirectory;
    private File appDirectory;
    private File internalStorageDirectory;

    private boolean waitingForExitConfirmation = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final View rootLayout =
                findViewById(R.id.rootLayout);

        if (Build.VERSION.SDK_INT >= 20
                && rootLayout != null) {

            rootLayout.setOnApplyWindowInsetsListener(
                    new View.OnApplyWindowInsetsListener() {
                        @Override
                        public WindowInsets onApplyWindowInsets(
                                View view,
                                WindowInsets insets
                        ) {
                            view.setPadding(
                                    view.getPaddingLeft(),
                                    insets.getSystemWindowInsetTop(),
                                    view.getPaddingRight(),
                                    insets.getSystemWindowInsetBottom()
                            );

                            return insets;
                        }
                    }
            );

            rootLayout.requestApplyInsets();
        }

        outputView =
                (TextView) findViewById(
                        R.id.terminal_output
                );

        inputView =
                (EditText) findViewById(
                        R.id.command_input
                );

        scrollView =
                (ScrollView) findViewById(
                        R.id.terminal_scroll
                );

        runButton =
                (Button) findViewById(
                        R.id.run_button
                );

        runButton.setOnClickListener(
                new View.OnClickListener() {
                    public void onClick(View view) {
                        submitCommand();
                    }
                }
        );

        appDirectory = getFilesDir();

        internalStorageDirectory =
                new File("/storage/emulated/0");

        currentDirectory = appDirectory;

        appendOutput("/ *\n");
        appendOutput("  * Shell for Android v0.0.4\n");
        appendOutput("  * Started: 2026-07-17\n");
        appendOutput("  *\n");
        appendOutput("  * Copyright (c) 2026 gittyalesio\n");
        appendOutput("  * Licensed under MIT License.\n");
        appendOutput("  * See LICENSE file in the GitHub project root for details.\n");
        appendOutput("  *\n");
        appendOutput("  * /\n\n");
        appendPrompt();

        inputView.setOnKeyListener(
                new View.OnKeyListener() {
                    public boolean onKey(
                            View view,
                            int keyCode,
                            KeyEvent event
                    ) {
                        if (keyCode == KeyEvent.KEYCODE_ENTER
                                && event.getAction()
                                == KeyEvent.ACTION_DOWN) {

                            submitCommand();
                            return true;
                        }

                        return false;
                    }
                }
        );

        inputView.requestFocus();
    }

    private void submitCommand() {
        String command =
                inputView.getText().toString();

        inputView.setText("");

        runCommand(command);

        restoreInputFocus();
    }
    private void restoreInputFocus() {
        inputView.requestFocus();

        int textLength =
                inputView.getText().length();

        inputView.setSelection(textLength);
    }

    private void runCommand(final String command) {
        final String trimmedCommand =
                command.trim();

        appendOutput(command + "\n");

        if (waitingForExitConfirmation) {
            waitingForExitConfirmation = false;

            if (trimmedCommand.equalsIgnoreCase("y")
                    || trimmedCommand.equalsIgnoreCase(
                    "yes"
            )) {

                finish();
                return;
            }

            appendOutput("Exit cancelled.\n");
            appendPrompt();
            return;
        }

        if (trimmedCommand.length() == 0) {
            appendPrompt();
            return;
        }

        if (trimmedCommand.equals("exit")) {
            waitingForExitConfirmation = true;

            appendOutput(
                    "Are you sure you want to exit? [Y/n] "
            );

            return;
        }

        if (trimmedCommand.equals("clear")) {
            outputView.setText("");
            appendPrompt();
            return;
        }

        if (isSimpleCdCommand(trimmedCommand)) {
            changeDirectory(trimmedCommand);
            appendPrompt();
            return;
        }

        if (currentProcess != null) {
            appendOutput(
                    "shell: another process is already running\n"
            );

            appendPrompt();
            return;
        }

        Thread commandThread =
                new Thread(
                        new Runnable() {
                            public void run() {
                                BufferedReader reader = null;

                                try {
                                    ProcessBuilder builder =
                                            new ProcessBuilder(
                                                    "/system/bin/sh",
                                                    "-c",
                                                    trimmedCommand
                                            );

                                    builder.redirectErrorStream(
                                            true
                                    );

                                    builder.directory(
                                            currentDirectory
                                    );

                                    currentProcess =
                                            builder.start();

                                    reader =
                                            new BufferedReader(
                                                    new InputStreamReader(
                                                            currentProcess
                                                                    .getInputStream()
                                                    )
                                            );

                                    String line;

                                    while ((line =
                                            reader.readLine())
                                            != null) {

                                        final String outputLine =
                                                line;

                                        runOnUiThread(
                                                new Runnable() {
                                                    public void run() {
                                                        appendOutput(
                                                                outputLine
                                                                        + "\n"
                                                        );
                                                    }
                                                }
                                        );
                                    }

                                    final int exitCode =
                                            currentProcess
                                                    .waitFor();

                                    runOnUiThread(
                                            new Runnable() {
                                                public void run() {
                                                    if (exitCode
                                                            != 0) {

                                                        appendOutput(
                                                                "[exit code "
                                                                        + exitCode
                                                                        + "]\n"
                                                        );
                                                    }

                                                    appendPrompt();
                                                }
                                            }
                                    );

                                } catch (
                                        final Exception exception
                                ) {
                                    runOnUiThread(
                                            new Runnable() {
                                                public void run() {
                                                    String message =
                                                            exception
                                                                    .getMessage();

                                                    if (message
                                                            == null) {

                                                        message =
                                                                "Unknown error";
                                                    }

                                                    appendOutput(
                                                            "shell: "
                                                                    + exception
                                                                    .getClass()
                                                                    .getSimpleName()
                                                                    + ": "
                                                                    + message
                                                                    + "\n"
                                                    );

                                                    appendPrompt();
                                                }
                                            }
                                    );

                                } finally {
                                    try {
                                        if (reader != null) {
                                            reader.close();
                                        }

                                    } catch (
                                            Exception ignored
                                    ) {
                                    }

                                    Process process =
                                            currentProcess;

                                    if (process != null) {
                                        process.destroy();
                                    }

                                    currentProcess = null;
                                }
                            }
                        }
                );

        commandThread.start();
    }

    private boolean isSimpleCdCommand(
            String command
    ) {
        if (command.equals("cd")) {
            return true;
        }

        if (!command.startsWith("cd ")) {
            return false;
        }

        if (command.indexOf('&') >= 0
                || command.indexOf('|') >= 0
                || command.indexOf(';') >= 0
                || command.indexOf('>') >= 0
                || command.indexOf('<') >= 0) {

            return false;
        }

        return true;
    }

    private void changeDirectory(
            String command
    ) {
        String path;

        if (command.equals("cd")) {
            path =
                    appDirectory.getAbsolutePath();

        } else {
            path =
                    command.substring(2).trim();
        }

        if (path.length() >= 2) {
            char firstCharacter =
                    path.charAt(0);

            char lastCharacter =
                    path.charAt(
                            path.length() - 1
                    );

            if ((firstCharacter == '"'
                    && lastCharacter == '"')
                    || (firstCharacter == '\''
                    && lastCharacter == '\'')) {

                path =
                        path.substring(
                                1,
                                path.length() - 1
                        );
            }
        }

        if (path.equals("$")) {
            path =
                    appDirectory.getAbsolutePath();

        } else if (path.startsWith("$/")) {
            path =
                    appDirectory.getAbsolutePath()
                            + path.substring(1);

        } else if (path.equals("~")) {
            path =
                    internalStorageDirectory
                            .getAbsolutePath();

        } else if (path.startsWith("~/")) {
            path =
                    internalStorageDirectory
                            .getAbsolutePath()
                            + path.substring(1);
        }

        File requestedDirectory =
                new File(path);

        if (!requestedDirectory.isAbsolute()) {
            requestedDirectory =
                    new File(
                            currentDirectory,
                            path
                    );
        }

        try {
            requestedDirectory =
                    requestedDirectory
                            .getCanonicalFile();

        } catch (Exception exception) {
            appendOutput(
                    "cd: "
                            + path
                            + ": "
                            + exception.getMessage()
                            + "\n"
            );

            return;
        }

        if (!requestedDirectory.exists()) {
            appendOutput(
                    "cd: "
                            + path
                            + ": No such file or directory\n"
            );

            return;
        }

        if (!requestedDirectory.isDirectory()) {
            appendOutput(
                    "cd: "
                            + path
                            + ": Not a directory\n"
            );

            return;
        }

        currentDirectory =
                requestedDirectory;
    }

    private void appendPrompt() {
        String currentPath =
                currentDirectory
                        .getAbsolutePath();

        String appPath =
                appDirectory
                        .getAbsolutePath();

        String internalPath =
                internalStorageDirectory
                        .getAbsolutePath();

        if (currentPath.equals(appPath)) {
            appendOutput("$ > ");

        } else if (currentPath.startsWith(
                appPath + "/"
        )) {
            appendOutput(
                    "$"
                            + currentPath.substring(
                            appPath.length()
                    )
                            + " > "
            );

        } else if (currentPath.equals(
                internalPath
        )) {
            appendOutput("~ > ");

        } else if (currentPath.startsWith(
                internalPath + "/"
        )) {
            appendOutput(
                    "~"
                            + currentPath.substring(
                            internalPath.length()
                    )
                            + " > "
            );

        } else {
            appendOutput(
                    currentPath + " > "
            );
        }
    }

    private void appendOutput(String text) {
        outputView.append(text);

        scrollView.post(
                new Runnable() {
                    public void run() {
                        scrollView.fullScroll(
                                ScrollView.FOCUS_DOWN
                        );
                    }
                }
        );
    }
}
