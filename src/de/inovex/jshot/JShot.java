package de.inovex.jshot;


import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Region;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * 
 * @author ruben.jenster@inovex.de
 *
 */
public class JShot {
	
	public static final int KEY_ENTER = 0x0d;
	
	private Display display;
	private Shell transparentShell;
	
	private String imageFilepath;
	private static boolean DEBUG;
	
	public static void main(String [] args) {
		
		DEBUG = Boolean.valueOf(System.getProperty("debug"));

		String filename;
		if (args.length > 0) {
			filename = args[0];
		} else {
			filename = "screenshot.png";
		}
		new JShot(filename);
		System.out.println(filename);
	}
	
	public JShot(String imageFilepath) {
		
		this.imageFilepath = imageFilepath;
		
		
		transparentShell = new Shell(SWT.NONE);
		transparentShell.setAlpha(0);
		display = transparentShell.getDisplay();
		transparentShell.setSize(display.getBounds().width, display.getBounds().height);

		JShotListener myListener = new JShotListener(this);
		transparentShell.addMouseListener(myListener);
		transparentShell.addMouseMoveListener(myListener);
		transparentShell.addKeyListener(myListener);
		
		transparentShell.open();
		
		while (!transparentShell.isDisposed()) {
			if (!display.readAndDispatch())
				display.sleep();
		}
		display.dispose();
	}

	public static Region drawFrame(Display display, final Shell shell, int width, int height, int border) {
		Region region = new Region(display);
		region.add(new Rectangle(0, 0, width, border));
		region.add(new Rectangle(0, 0, border, height));
		region.add(new Rectangle(width-border, 0, border, height));
		region.add(new Rectangle(0, height-border, width, border));
		shell.setRegion(region);
		return region;
	}
	
	public static class Frame {
		
		private Region region;
		private int borderWidth;
		
		private Shell shell;
		private int startX;
		private int startY;
		
		private int x1;
		private int y1;
		private int x2;
		private int y2;
		private boolean top;
		private boolean left;

		public void clear() {
			if (region != null) {
				region.dispose();
			}
			shell.setRegion(new Region());
		}
		
		public Frame(Shell parent) {
			this(parent, 5, new Color(parent.getDisplay(), 255, 0, 0));
		}
		
		public Frame(Shell parent, int borderWidth, Color borderColor) {
			// create shell that is used for painting the frame
			shell = new Shell(parent, SWT.NO_TRIM);
			this.borderWidth = borderWidth;
			shell.setBackground(borderColor);
			shell.setSize(parent.getDisplay().getBounds().width, parent.getDisplay().getBounds().height);
			this.clear();
			shell.setVisible(true);
		}
		
		public synchronized void setStart(int x, int y) {
			this.startX = x;
			this.startY = y;
		}
		
		public synchronized void draw(int endX, int endY) {

			// dispose previous region
			if (region != null) {
				region.dispose();
			}
			
			Rectangle topBorder = null;
			Rectangle rightBorder = null;
			Rectangle bottomBorder = null;
			Rectangle leftBorder = null;

			int origin = calculateOrigin(startX, startY, endX, endY);
				
			top = (origin & TOP_LEFT) > 0 || (origin & TOP_RIGHT) > 0;
			left = (origin & TOP_LEFT) > 0 || (origin & BOTTOM_LEFT) > 0;  
			
			/*
			 * distinguish between top and bottom
			 * left- and right border are calculated the same for top or bottom
			 */
			
			int borderX = 0;
			int borderY = 0;
			
			if (top) {
				y1 = startY;
				y2 = endY;
				borderY = borderWidth;
			} else { // bottom
				y1 = endY;
				y2 = startY;
			}
			
			if (left) {
				x1 = startX;
				x2 = endX;
				borderX = borderWidth;
			} else { // right
				x1 = endX;
				x2 = startX;
			}
			
			int width = x2 - x1 + borderWidth;
			int height = y2 - y1 + borderWidth;
			
			
			/*
			topBorder = new Rectangle(x1 - borderX, y1 - borderY, width, borderWidth);
			rightBorder = new Rectangle(x2 - borderX, y1 - borderY, borderWidth, height);
			bottomBorder = new Rectangle(x1 - borderX, y2 - borderY, width, borderWidth);
			leftBorder = new Rectangle(x1 - borderX, y1 - borderY, borderWidth, height);
			*/
			// create new region
			try {
				region = new Region();
				region.add(new Rectangle(x1-borderWidth, y1-borderWidth, x2-x1, y2-y1));
				region.subtract(new Rectangle(x1, y1, x2-x1-2*borderWidth, y2-y1-2*borderWidth));
				/*
				region.add(topBorder);
				region.add(leftBorder);
				region.add(rightBorder);
				region.add(bottomBorder);
				*/
				shell.setRegion(region);
				shell.layout();
			
				debug("###################################");
				debug("Point1: x1[%s], y1[%s]", startX, startY);
				debug("Point2: x1[%s], y1[%s]", endX, endY);
				debug("Point1c switched: x1[%s], y1[%s]", x1, y1);
				debug("Point2c switched: x2[%s], y2[%s]", x2, y2);
				debug("topBorder: [%s]", topBorder);
				debug("rightBorder: [%s]", rightBorder);
				debug("bottomBorder: [%s]", bottomBorder);
				debug("leftBorder: [%s]", leftBorder);
				debug("###################################");

			} catch (IllegalArgumentException e) {
				// ignore
			}
		}
		
		public synchronized Rectangle getBounds() {
			if (top && left) {
				return new Rectangle(x1, y1, x2-x1-borderWidth, y2-y1-borderWidth);
			} else if (top && !left) {
				// TOP_RIGHT, left border
				return new Rectangle(x1+borderWidth, y1, x2-x1-borderWidth, y2-y1-borderWidth);
			} else if (!top && left) {
				// BOTTOM_LEFT, top border
				return new Rectangle(x1, y1+borderWidth, x2-x1-borderWidth, y2-y1-borderWidth);
			} else {
				// BOTTOM_RIGHT, left and top border
				return new Rectangle(x1+borderWidth, y1+borderWidth, x2-x1-borderWidth, y2-y1-borderWidth);
			}
		}
	}
	
	public static void debug(String format, Object ... params) {
		if (DEBUG) {
			if (params.length == 0) {
				System.out.println(format);
			} else {
				System.out.println(String.format(format, params));
			}
		}
	}
	
	public static final int TOP_LEFT = 0x01;
	public static final int TOP_RIGHT = 0x02;
	public static final int BOTTOM_RIGHT = 0x04;
	public static final int BOTTOM_LEFT = 0x08;
	
	public static int calculateOrigin(int x1, int y1, int x2, int y2) {
		int width = x2 - x1;
		int height = y2 - y1;
		return calculateOrigin(width, height);
	}

	public static int calculateOrigin(int width, int height) {
		
		if (width > 0 && height > 0) {
			return TOP_LEFT;
		} else if (width < 0 && height > 0) {
			return TOP_RIGHT;
		} else if (width < 0 && height < 0) {
			return BOTTOM_RIGHT;
		} else {
			return BOTTOM_LEFT;
		}
	}
	
	public void shot(Rectangle rectangle) {
		GC gc = new GC(display);
		final Image image = new Image(display, rectangle);
		gc.copyArea(image, rectangle.x, rectangle.y);
		gc.dispose();
		
		ScrolledComposite sc = new ScrolledComposite (transparentShell, SWT.V_SCROLL | SWT.H_SCROLL);
		Canvas canvas = new Canvas(sc, SWT.NONE);
		sc.setContent(canvas);
		canvas.setBounds(display.getBounds ());
		canvas.addPaintListener(new PaintListener() {
			public void paintControl(PaintEvent e) {
				e.gc.drawImage(image, 0, 0);
			}
		});

		ImageLoader imageLoader = new ImageLoader();
		imageLoader.data = new ImageData[]{image.getImageData()};
		imageLoader.save(imageFilepath, SWT.IMAGE_PNG);
	}
	
	public void quit() {
		transparentShell.dispose();
	};
	
	public Shell getShell() {
		return transparentShell;
	}
}
