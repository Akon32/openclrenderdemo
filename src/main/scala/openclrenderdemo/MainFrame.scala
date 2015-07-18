package openclrenderdemo

import java.awt._
import java.awt.event._
import java.awt.geom.{AffineTransform, Point2D}
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.media.jai.PerspectiveTransform
import javax.swing._

import openclrenderdemo.MainFrame.time

class MainFrame extends JFrame("OpenCL Render Demo") {

  private var image: Option[BufferedImage] = None

  private var rasterizer: ImageRasterizer = new JavaRasterizer

  val perspective = new PerspectiveTransform()

  val viewTransform1 = new AffineTransform()

  val viewTransform2 = new AffineTransform()

  var transformEnabled = false

  def currentTransform = if (transformEnabled) viewTransform2 else viewTransform1

  lazy val imagePanel = new JPanel {
    override def paint(g: Graphics): Unit = doPaint(g.asInstanceOf[Graphics2D])
  }
  lazy val toolbar = {
    val t = new JToolBar("View")
    t.add {
      val b = new JToggleButton("Transform", transformEnabled)
      b.addActionListener(new ActionListener {
        override def actionPerformed(actionEvent: ActionEvent): Unit = {
          transformEnabled = b.isSelected
          repaint()
        }
      })
      b
    }
    t.add {
      val b = new JToggleButton("OpenCL", false)
      b.addActionListener(new ActionListener {
        override def actionPerformed(actionEvent: ActionEvent): Unit = {
          rasterizer.close()
          rasterizer = if (b.isSelected && OpenCLUtils.isAvailable) new OpenCLRasterizer else new JavaRasterizer
          image foreach rasterizer.setImage
          repaint()
        }
      })
      b.setEnabled(OpenCLUtils.isAvailable)
      b
    }
    t.add {
      val b = new JButton("dump opencl program")
      b.addActionListener(new ActionListener {
        override def actionPerformed(actionEvent: ActionEvent): Unit = {
          rasterizer match {
            case r: OpenCLRasterizer => r.dumpProgram()
            case _ =>
          }
        }
      })
      b.setEnabled(OpenCLUtils.isAvailable)
      b
    }
    t
  }

  def doPaint(g2: Graphics2D): Unit = {
    time("paint image") {
      image foreach { img =>
        if (transformEnabled) {
          val im = rasterizer.rasterize(imagePanel.getWidth, imagePanel.getHeight, viewTransform2, perspective)
          g2.drawImage(im, 0, 0, null)
        } else {
          g2.drawImage(img, viewTransform1, null)
        }
      }
    }
    val ptr = new PerspectiveTransform()
    if (transformEnabled)
      ptr.concatenate(perspective.createInverse())
    ptr.concatenate(currentTransform)
    g2.setColor(Color.RED)
    g2.draw {
      val s = new Polygon()
      pointsArray foreach { p =>
        val pt = ptr.transform(p, null)
        s.addPoint(pt.getX.toInt, pt.getY.toInt)
      }
      s
    }
  }

  private val pointsArray = Array[Point2D](
    new Point2D.Double(454, 530),
    new Point2D.Double(651, 528),
    new Point2D.Double(703, 635),
    new Point2D.Double(409, 634))
  private var pointIndex = 0

  def updatePerspective(): Unit = {
    perspective.setTransform(PerspectiveTransform.getQuadToQuad(
      0.0, 0.0,
      100.0, 0.0,
      100.0, 100.0,
      0.0, 100.0,
      pointsArray(0).getX, pointsArray(0).getY,
      pointsArray(1).getX, pointsArray(1).getY,
      pointsArray(2).getX, pointsArray(2).getY,
      pointsArray(3).getX, pointsArray(3).getY))
    // println(pointsArray mkString ";")
  }

  def pointClicked(p: Point) = {
    if (!transformEnabled) {
      pointsArray(pointIndex) = currentTransform.inverseTransform(new Point2D.Double(p.x, p.y), null)
      pointIndex = (pointIndex + 1) % pointsArray.size
      updatePerspective()
      repaint()
    }
  }

  def setImage(img: BufferedImage): Unit = {
    image = Some(img)
    rasterizer.setImage(img)
    repaint()
  }

  private def init() {
    val root = getContentPane
    root.setLayout(new BorderLayout())
    root.add(toolbar, BorderLayout.NORTH)
    root.add(imagePanel, BorderLayout.CENTER)
    imagePanel.setDoubleBuffered(true)
    setPreferredSize(new Dimension(800, 600))
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
    setLocationByPlatform(true)
    addWindowListener(new WindowAdapter {
      override def windowClosing(e: WindowEvent): Unit = rasterizer.close()
    })
    val mouseListener = new MouseAdapter {
      var lastPoint = new Point(-1, -1)
      var pressedButton = -1

      override def mouseWheelMoved(e: MouseWheelEvent): Unit = {
        val (x, y) = (e.getPoint.getX, e.getPoint.getY)
        val k = math.pow(0.8, e.getWheelRotation)
        val (dx, dy) = (currentTransform.getTranslateX, currentTransform.getTranslateY)
        val (sx, sy) = (currentTransform.getScaleX, currentTransform.getScaleY)
        currentTransform.setTransform(
          sx * k, 0,
          0, sy * k,
          dx + (1 - k) * (x - dx),
          dy + (1 - k) * (y - dy))
        repaint()
      }

      override def mousePressed(e: MouseEvent): Unit = {
        lastPoint = e.getPoint
        pressedButton = e.getButton
      }

      override def mouseEntered(e: MouseEvent): Unit = lastPoint = e.getPoint

      override def mouseDragged(e: MouseEvent): Unit = {
        val dx = e.getX - lastPoint.x
        val dy = e.getY - lastPoint.y
        lastPoint = e.getPoint
        currentTransform.translate(dx / currentTransform.getScaleX, dy / currentTransform.getScaleY)
        repaint()
      }

      override def mouseClicked(e: MouseEvent): Unit = {
        if (e.getButton == MouseEvent.BUTTON1)
          pointClicked(e.getPoint)
      }
    }
    imagePanel.addMouseWheelListener(mouseListener)
    imagePanel.addMouseListener(mouseListener)
    imagePanel.addMouseMotionListener(mouseListener)
    viewTransform1.scale(0.8, 0.8)
    updatePerspective()
    pack()
  }

  init()
}

object MainFrame extends App {
  run()

  def run() {
    try {
      val frame = new MainFrame()
      frame.setImage(ImageIO.read(new File("data/image.jpg")))
      frame.setVisible(true)
    } catch {
      case e: Throwable =>
        e.printStackTrace()
        System.exit(1)
    }
  }

  def time[T](caption: String)(code: => T): T = {
    val start = System.nanoTime()
    try
      code: T
    finally
      println(s"$caption: ${(System.nanoTime() - start) * 1e-6}ms")
  }
}
