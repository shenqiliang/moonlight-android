/**
 * ComboButton - A configurable button that can trigger multiple button inputs simultaneously
 */

package com.limelight.binding.input.virtual_controller;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import com.limelight.nvstream.input.ControllerPacket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A combo button that can be configured to trigger multiple button inputs at once.
 */
public class ComboButton extends VirtualControllerElement {

    // Available button options for combo configuration
    private static final ButtonOption[] AVAILABLE_BUTTONS = {
            new ButtonOption("A", ControllerPacket.A_FLAG),
            new ButtonOption("B", ControllerPacket.B_FLAG),
            new ButtonOption("X", ControllerPacket.X_FLAG),
            new ButtonOption("Y", ControllerPacket.Y_FLAG),
            new ButtonOption("LB", ControllerPacket.LB_FLAG),
            new ButtonOption("RB", ControllerPacket.RB_FLAG),
            new ButtonOption("LT", -1), // Special handling for trigger
            new ButtonOption("RT", -1), // Special handling for trigger
            new ButtonOption("LS (L3)", ControllerPacket.LS_CLK_FLAG),
            new ButtonOption("RS (R3)", ControllerPacket.RS_CLK_FLAG),
            new ButtonOption("START", ControllerPacket.PLAY_FLAG),
            new ButtonOption("BACK", ControllerPacket.BACK_FLAG),
            new ButtonOption("UP", ControllerPacket.UP_FLAG),
            new ButtonOption("DOWN", ControllerPacket.DOWN_FLAG),
            new ButtonOption("LEFT", ControllerPacket.LEFT_FLAG),
            new ButtonOption("RIGHT", ControllerPacket.RIGHT_FLAG),
    };

    private static class ButtonOption {
        String name;
        int flag;

        ButtonOption(String name, int flag) {
            this.name = name;
            this.flag = flag;
        }
    }

    private List<Integer> buttonFlags = new ArrayList<>();
    private List<Integer> triggerValues = new ArrayList<>(); // For LT/RT triggers
    private String label = "COMBO";
    private final Paint paint = new Paint();
    private final RectF rect = new RectF();
    private boolean isPressed = false;

    // Trigger button references (set during configuration)
    private boolean includeLeftTrigger = false;
    private boolean includeRightTrigger = false;
    private byte leftTriggerValue = (byte) 0xFF;
    private byte rightTriggerValue = (byte) 0xFF;

    public ComboButton(VirtualController controller, Context context) {
        super(controller, context, EID_COMBO_BUTTON);
    }

    /**
     * Set the button combination flags
     */
    public void setButtonFlags(List<Integer> flags) {
        this.buttonFlags.clear();
        this.buttonFlags.addAll(flags);
        updateLabel();
        invalidate();
    }

    /**
     * Set whether to include left/right triggers
     */
    public void setTriggers(boolean leftTrigger, boolean rightTrigger) {
        this.includeLeftTrigger = leftTrigger;
        this.includeRightTrigger = rightTrigger;
        updateLabel();
        invalidate();
    }

    /**
     * Update the button label based on configured buttons
     */
    private void updateLabel() {
        if (buttonFlags.isEmpty() && !includeLeftTrigger && !includeRightTrigger) {
            label = "COMBO";
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int flag : buttonFlags) {
            String name = getButtonName(flag);
            if (name != null) {
                if (sb.length() > 0) sb.append("+");
                sb.append(name);
            }
        }
        if (includeLeftTrigger) {
            if (sb.length() > 0) sb.append("+");
            sb.append("LT");
        }
        if (includeRightTrigger) {
            if (sb.length() > 0) sb.append("+");
            sb.append("RT");
        }
        label = sb.toString();
    }

    /**
     * Get button name from flag
     */
    private String getButtonName(int flag) {
        for (ButtonOption option : AVAILABLE_BUTTONS) {
            if (option.flag == flag) {
                return option.name;
            }
        }
        return null;
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        canvas.drawColor(Color.TRANSPARENT);

        paint.setTextSize(getPercent(getWidth(), 15));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStrokeWidth(getDefaultStrokeWidth());

        paint.setColor(isPressed ? pressedColor : getDefaultColor());
        paint.setStyle(Paint.Style.STROKE);

        rect.left = rect.top = paint.getStrokeWidth();
        rect.right = getWidth() - rect.left;
        rect.bottom = getHeight() - rect.top;

        // Draw rounded rectangle for combo button
        float cornerRadius = getPercent(getWidth(), 10);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);

        // Draw label
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setStrokeWidth(getDefaultStrokeWidth() / 2);

        // Adjust text size to fit
        float textSize = paint.getTextSize();
        while (paint.measureText(label) > getWidth() - 10 && textSize > 8) {
            textSize -= 1;
            paint.setTextSize(textSize);
        }

        canvas.drawText(label, getPercent(getWidth(), 50), getPercent(getHeight(), 55), paint);
    }

    @Override
    public boolean onElementTouchEvent(android.view.MotionEvent event) {
        int action = event.getActionMasked();

        switch (action) {
            case android.view.MotionEvent.ACTION_DOWN: {
                isPressed = true;
                applyButtonState(true);
                invalidate();
                return true;
            }
            case android.view.MotionEvent.ACTION_CANCEL:
            case android.view.MotionEvent.ACTION_UP: {
                isPressed = false;
                applyButtonState(false);
                invalidate();
                return true;
            }
        }
        return true;
    }

    /**
     * Apply or release all configured buttons
     */
    private void applyButtonState(boolean pressed) {
        VirtualController.ControllerInputContext inputContext =
                virtualController.getControllerInputContext();

        if (pressed) {
            // Set all configured button flags
            for (int flag : buttonFlags) {
                inputContext.inputMap |= flag;
            }

            // Apply trigger values if configured
            if (includeLeftTrigger) {
                inputContext.leftTrigger = leftTriggerValue;
            }
            if (includeRightTrigger) {
                inputContext.rightTrigger = rightTriggerValue;
            }
        } else {
            // Clear all configured button flags
            for (int flag : buttonFlags) {
                inputContext.inputMap &= ~flag;
            }

            // Reset trigger values if configured
            if (includeLeftTrigger) {
                inputContext.leftTrigger = 0;
            }
            if (includeRightTrigger) {
                inputContext.rightTrigger = 0;
            }
        }

        virtualController.sendControllerInputContext();
    }

    /**
     * Show configuration dialog to select buttons for the combo
     */
    public void showComboConfigurationDialog() {
        String[] buttonNames = new String[AVAILABLE_BUTTONS.length];
        final boolean[] selectedItems = new boolean[AVAILABLE_BUTTONS.length];

        for (int i = 0; i < AVAILABLE_BUTTONS.length; i++) {
            buttonNames[i] = AVAILABLE_BUTTONS[i].name;
            // Check if this button is already in the combo
            if (buttonFlags.contains(AVAILABLE_BUTTONS[i].flag)) {
                selectedItems[i] = true;
            }
            // Special handling for triggers
            if (AVAILABLE_BUTTONS[i].name.equals("LT") && includeLeftTrigger) {
                selectedItems[i] = true;
            }
            if (AVAILABLE_BUTTONS[i].name.equals("RT") && includeRightTrigger) {
                selectedItems[i] = true;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Select Buttons for Combo");
        builder.setMultiChoiceItems(buttonNames, selectedItems, new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                // Handle trigger selection specially
                if (AVAILABLE_BUTTONS[which].name.equals("LT")) {
                    includeLeftTrigger = isChecked;
                } else if (AVAILABLE_BUTTONS[which].name.equals("RT")) {
                    includeRightTrigger = isChecked;
                }
            }
        });
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Apply selected buttons
                buttonFlags.clear();

                for (int i = 0; i < selectedItems.length; i++) {
                    if (selectedItems[i]) {
                        // Skip triggers as they are handled separately
                        if (!AVAILABLE_BUTTONS[i].name.equals("LT") && 
                            !AVAILABLE_BUTTONS[i].name.equals("RT")) {
                            buttonFlags.add(AVAILABLE_BUTTONS[i].flag);
                        }
                    }
                }

                updateLabel();
                invalidate();

                // Save configuration
                VirtualControllerConfigurationLoader.saveProfile(virtualController, getContext());
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    @Override
    protected void showConfigurationDialog() {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getContext());
        alertBuilder.setTitle("Combo Button Configuration");

        CharSequence functions[] = new CharSequence[]{
                "Select Buttons",
                "Move",
                "Resize",
                "Cancel"
        };

        alertBuilder.setItems(functions, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0: {
                        showComboConfigurationDialog();
                        break;
                    }
                    case 1: {
                        actionEnableMove();
                        break;
                    }
                    case 2: {
                        actionEnableResize();
                        break;
                    }
                    default: {
                        actionCancel();
                        break;
                    }
                }
            }
        });
        AlertDialog alert = alertBuilder.create();
        alert.show();
    }

    @Override
    public JSONObject getConfiguration() throws JSONException {
        JSONObject configuration = super.getConfiguration();

        // Save button flags
        JSONArray flagsArray = new JSONArray();
        for (int flag : buttonFlags) {
            flagsArray.put(flag);
        }
        configuration.put("BUTTON_FLAGS", flagsArray);

        // Save trigger settings
        configuration.put("INCLUDE_LT", includeLeftTrigger);
        configuration.put("INCLUDE_RT", includeRightTrigger);

        return configuration;
    }

    @Override
    public void loadConfiguration(JSONObject configuration) throws JSONException {
        super.loadConfiguration(configuration);

        // Load button flags
        buttonFlags.clear();
        if (configuration.has("BUTTON_FLAGS")) {
            JSONArray flagsArray = configuration.getJSONArray("BUTTON_FLAGS");
            for (int i = 0; i < flagsArray.length(); i++) {
                buttonFlags.add(flagsArray.getInt(i));
            }
        }

        // Load trigger settings
        includeLeftTrigger = configuration.optBoolean("INCLUDE_LT", false);
        includeRightTrigger = configuration.optBoolean("INCLUDE_RT", false);

        updateLabel();
        invalidate();
    }
}
