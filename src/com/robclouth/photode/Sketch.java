package com.robclouth.photode;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import processing.core.PApplet;
import processing.core.PGraphics;
import processing.core.PImage;
import android.view.View;
import dalvik.system.DexClassLoader;

public class Sketch {
	MainActivity main;
	View listItem;
	File sketchFolder;
	public String name, className, author, description, guid;
	ArrayList<Parameter> parameters;
	PApplet childApplet;
	Method processMethod;

	public Sketch(MainActivity main, File sketchFolder) {
		this.main = main;
		this.sketchFolder = sketchFolder;
		parameters = new ArrayList<Parameter>();
	}

	public boolean parseSketchInfo() {
		Document doc = null;
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder db = dbf.newDocumentBuilder();
			doc = db.parse(new FileInputStream(sketchFolder + "/sketch.def"));
			Element root = (Element) doc.getElementsByTagName("sketch").item(0);

			name = getChild(root, "name").getTextContent();
			author = getChild(root, "author").getTextContent();
			description = getChild(root, "description").getTextContent();
			guid = getChild(root, "guid").getTextContent();
			className = getChild(root, "className").getTextContent();

			Node parametersElement = getChild(root, "parameters");

			if (parametersElement != null) {
				NodeList parameterList = parametersElement.getChildNodes();
				for (int i = 0; i < parameterList.getLength(); i++) {
					if (!parameterList.item(i).getNodeName().equals("parameter"))
						continue;
					Element parameterNode = (Element) parameterList.item(i);
					String paramName = getChild(parameterNode, "name").getTextContent();
					String paramDescription = getChild(parameterNode, "description").getTextContent();
					String paramType = getChild(parameterNode, "type").getTextContent();

					float paramMin = 0, paramMax = 0;
					String options = null;
					if (paramType.equals("String")) {
						options = getChild(parameterNode, "options").getTextContent();
					} else {
						paramMin = Float.parseFloat(getChild(parameterNode, "min").getTextContent());
						paramMax = Float.parseFloat(getChild(parameterNode, "max").getTextContent());
					}
					String paramField = getChild(parameterNode, "variable").getTextContent();

					Parameter parameter = new Parameter(this, paramName, paramDescription, paramType, paramMin, paramMax, paramField, options);
					parameters.add(parameter);
				}
			}
		} catch (Exception e) {
			return false;
		}

		return true;
	}

	private Node getChild(Node element, String tag) {
		NodeList nodes = element.getChildNodes();
		for (int i = 0; i < nodes.getLength(); i++) {
			Node node = nodes.item(i);
			if (node.getNodeName().equals(tag))
				return node;
		}
		return null;
	}

	public boolean init() {
		try {
			final String sketchPath = sketchFolder + "/sketch.jar";
			final File dexDir = main.getDir("dex_" + guid, 0);
			final DexClassLoader classloader = new DexClassLoader(sketchPath, dexDir.getAbsolutePath(), null, this.getClass().getClassLoader());

			// load class and find process method
			Class<?> sketchClazz = classloader.loadClass(className);
			childApplet = (PApplet) sketchClazz.newInstance();
			processMethod = childApplet.getClass().getDeclaredMethod("processImage", PImage.class, PGraphics.class);

			// find parameters
			Field[] fields = sketchClazz.getDeclaredFields();
			for (Parameter parameter : parameters) {
				for (Field field : fields) {
					if (field.getName().equals(parameter.fieldName)) {
						// make the fields accessible
						field.setAccessible(true);
						parameter.field = field;
					}
				}
			}
		} catch (Exception e) {
			return false;
		}

		return true;
	}

	public PGraphics processBitmap(PImage in, PGraphics out) {
		try {
			out.beginDraw();
			processMethod.invoke(childApplet, in, out);
			out.endDraw();
			out.updatePixels();
			return out;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
}
