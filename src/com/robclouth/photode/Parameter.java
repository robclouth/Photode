package com.robclouth.photode;

import java.lang.reflect.Field;

import android.widget.SeekBar;
import android.widget.TextView;

public class Parameter {
	Sketch sketch;
	String name, description, type, fieldName;
	float value, min, max;
	Field field;
	SeekBar slider;
	public TextView textView;
	public String[] options;

	Parameter(Sketch sketch, String name, String description, String type, float min, float max, String fieldName, String options) {
		this.sketch = sketch;
		this.name = name;
		this.description = description;
		this.type = type;
		this.min = min;
		this.max = max;
		this.fieldName = fieldName;

		if (options != null) {
			this.options = options.split(",");
			for (String option : this.options)
				option = option.trim();
			this.min = 0;
			this.max = this.options.length - 1;
		}

	}

	public void setValueFromSlider(int sliderValue) {
		setValue((sliderValue / (float) 100) * (max - min) + min);
	}

	public void updateValueFromField() {
		try {
			Object obj = field.get(sketch.childApplet);
			if (field.getType() == float.class)
				value = (Float) obj;
			else if (field.getType() == int.class)
				value = (Integer) obj;
			else if (field.getType() == String.class) {
				String option = (String) obj;
				value = 0;
				for (int i = 0; i < options.length; i++) {
					if (option.equals(options[i])) {
						value = i;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public String getValueAsString() {
		updateValueFromField();
		if (field.getType() == float.class)
			return String.format("%.2f", value);
		else if (field.getType() == int.class)
			return Integer.toString((int) value);
		else if (field.getType() == String.class)
			return options[(int) value];
		return "";
	}

	public int getValueToSlider() {
		updateValueFromField();
		return (int) (((float) (value - min) / (max - min)) * 100);
	}

	public void setValue(float v) {
		try {
			value = v;
			if (field.getType() == float.class)
				field.setFloat(sketch.childApplet, value);
			else if (field.getType() == int.class)
				field.setInt(sketch.childApplet, (int) value);
			else if (field.getType() == String.class) {
				String option = options[Math.round(value)];
				field.set(sketch.childApplet, option);
			}
			updateUI();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public void updateUI() {
		slider.setProgress(getValueToSlider());
		textView.setText(getValueAsString());
	}
}
