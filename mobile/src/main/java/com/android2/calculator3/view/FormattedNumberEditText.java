/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android2.calculator3.view;

import android.content.Context;
import android.text.Editable;
import android.text.Html;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;

import com.android2.calculator3.R;
import com.xlythe.math.BaseModule;
import com.xlythe.math.Constants;
import com.xlythe.math.EquationFormatter;
import com.xlythe.math.Solver;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * FormattedNumberEditText adds more advanced functionality to NumberEditText.
 *
 * Commas will appear as numbers are typed, exponents will be raised, and backspacing
 * on sin( and log( will remove the whole word. Because of the formatting, getText() will
 * no longer return the correct value. getCleanText() has been added instead.
 * */
public class FormattedNumberEditText extends NumberEditText {
    private final Set<TextWatcher> mTextWatchers = new HashSet<>();
    private boolean mTextWatchersEnabled = true;
    private final TextWatcher mTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            if (!mTextWatchersEnabled || mSolver == null || getSelectionStart() == -1) return;
            mTextWatchersEnabled = false;

            String text = removeFormatting(s.toString());

            // Get the selection handle, since we're setting text and that'll overwrite it
            mSelectionHandle = getSelectionStart();

            // Adjust the handle by removing any comas or spacing to the left
            String cs = s.subSequence(0, mSelectionHandle).toString();
            mSelectionHandle -= TextUtil.countOccurrences(cs, mSolver.getBaseModule().getSeparator());

            // Update the text with formatted (comas, etc) text
            setText(formatText(text));
            setSelection(mSelectionHandle);

            mTextWatchersEnabled = true;
        }
    };
    private EquationFormatter mEquationFormatter;
    private int mSelectionHandle = 0;
    private Solver mSolver;
    private List<String> mKeywords;

    public FormattedNumberEditText(Context context) {
        super(context);
        setUp(context, null);
    }

    public FormattedNumberEditText(Context context, AttributeSet attr) {
        super(context, attr);
        setUp(context, attr);
    }

    private void setUp(Context context, AttributeSet attrs) {
        // Display ^ , and other visual cues
        mEquationFormatter = new EquationFormatter();
        addTextChangedListener(mTextWatcher);
        mKeywords = Arrays.asList(
                context.getString(R.string.fun_arcsin) + "(",
                context.getString(R.string.fun_arccos) + "(",
                context.getString(R.string.fun_arctan) + "(",
                context.getString(R.string.fun_sin) + "(",
                context.getString(R.string.fun_cos) + "(",
                context.getString(R.string.fun_tan) + "(",
                context.getString(R.string.fun_log) + "(",
                context.getString(R.string.mod) + "(",
                context.getString(R.string.fun_ln) + "(",
                context.getString(R.string.fun_det) + "(",
                context.getString(R.string.dx),
                context.getString(R.string.dy),
                context.getString(R.string.cbrt) + "(");
    }

    @Override
    public void addTextChangedListener(TextWatcher watcher) {
        if (watcher.equals(mTextWatcher)) {
            super.addTextChangedListener(watcher);
        } else {
            mTextWatchers.add(watcher);
        }
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        if (mTextWatchersEnabled) {
            for (TextWatcher textWatcher : mTextWatchers) {
                textWatcher.beforeTextChanged(getCleanText(), 0, 0, 0);
            }
        }
        super.setText(text, type);
        if (text != null) {
            setSelection(getText().length());
        }
        invalidateTextSize();
        if (mTextWatchersEnabled) {
            for (TextWatcher textWatcher : mTextWatchers) {
                textWatcher.afterTextChanged(getEditableFactory().newEditable(getCleanText()));
                textWatcher.onTextChanged(getCleanText(), 0, 0, 0);
            }
        }
    }

    public String getCleanText() {
        return toString();
    }

    public void insert(String text) {
        if (getSelectionStart() == -1) {
            setText(text);
            return;
        }

        if (mTextWatchersEnabled) {
            for (TextWatcher textWatcher : mTextWatchers) {
                textWatcher.beforeTextChanged(getCleanText(), 0, 0, 0);
            }
        }
        getText().insert(getSelectionStart(), text);
        invalidateTextSize();
        if (mTextWatchersEnabled) {
            for (TextWatcher textWatcher : mTextWatchers) {
                textWatcher.afterTextChanged(getEditableFactory().newEditable(getCleanText()));
                textWatcher.onTextChanged(getCleanText(), 0, 0, 0);
            }
        }
    }

    public void clear() {
        setText(null);
    }

    public boolean isCursorModified() {
        return getSelectionStart() != getText().length();
    }

    public void next() {
        if (getSelectionStart() == getText().length()) {
            setSelection(0);
        } else {
            setSelection(getSelectionStart() + 1);
        }
    }

    @Override
    public void backspace() {
        // Check and remove keywords
        String text = getText().toString();
        int selectionHandle = getSelectionStart();
        String textBeforeInsertionHandle = text.substring(0, selectionHandle);
        String textAfterInsertionHandle = text.substring(selectionHandle, text.length());

        for(String s : mKeywords) {
            if(textBeforeInsertionHandle.endsWith(s)) {
                int deletionLength = s.length();
                String newText = textBeforeInsertionHandle.substring(0, textBeforeInsertionHandle.length() - deletionLength) + textAfterInsertionHandle;
                setText(newText);
                setSelection(selectionHandle - deletionLength);
                return;
            }
        }

        super.backspace();
    }

    @Override
    public void setSelection(int index) {
        super.setSelection(Math.max(0, Math.min(getText().length(), index)));
    }

    public void setSolver(Solver solver) {
        mSolver = solver;
    }

    private String removeFormatting(String input) {
        input = input.replace(Constants.POWER_PLACEHOLDER, Constants.POWER);
        if(mSolver != null) {
            input = input.replace(String.valueOf(mSolver.getBaseModule().getSeparator()), "");
        }
        return input;
    }

    private Spanned formatText(String input) {
        if(mSolver != null) {
            // Add grouping, and then split on the selection handle
            // which is saved as a unique char
            String grouped = mEquationFormatter.addComas(mSolver, input, mSelectionHandle);
            if (grouped.contains(String.valueOf(BaseModule.SELECTION_HANDLE))) {
                String[] temp = grouped.split(String.valueOf(BaseModule.SELECTION_HANDLE));
                mSelectionHandle = temp[0].length();
                input = "";
                for (String s : temp) {
                    input += s;
                }
            } else {
                input = grouped;
                mSelectionHandle = input.length();
            }
        }

        return Html.fromHtml(mEquationFormatter.insertSupScripts(input));
    }

    @Override
    public String toString() {
        return removeFormatting(getText().toString());
    }
}
