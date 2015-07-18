package openclrenderdemo

import java.awt.geom.{AffineTransform, Point2D}
import java.awt.image.BufferedImage
import javax.media.jai.PerspectiveTransform

import openclrenderdemo.ImageRasterizer.Params

class JavaRasterizer extends ImageRasterizer {

  private var isInitialized = false

  private var imageBuf: Array[Int] = _
  private var (imageWidth, imageHeight) = (0, 0)

  private object imageMemory {
    var outputImage: BufferedImage = _
    var (w, h) = (0, 0)
    var outputArray: Array[Int] = _

    def resize(w: Int, h: Int): Unit = {
      if (w != this.w || h != this.h) {
        outputArray = Array.ofDim[Int](w * h)
        outputImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        this.w = w
        this.h = h
      }
    }

    def release(): Unit = {
      outputImage = null
      outputArray = null
      w = 0
      h = 0
    }
  }

  override def setImage(image: BufferedImage): Unit = {
    println("JavaRasterizer: setImage()...")
    imageWidth = image.getWidth
    imageHeight = image.getHeight
    imageBuf = Array.ofDim[Int](imageWidth * imageHeight)
    image.getRGB(0, 0, imageWidth, imageHeight, imageBuf, 0, imageWidth)
  }


  override def close(): Unit = {
    imageBuf = null
    imageWidth = 0
    imageHeight = 0
    println("JavaRasterizer: released.")
  }

  def init(w: Int, h: Int): Unit = {
    imageMemory.resize(w, h)
  }

  private var lastParams: Params = _

  override def rasterize(width: Int, height: Int, viewTransform: AffineTransform, perspectiveTransform: PerspectiveTransform): BufferedImage = {
    val currentParams = Params.create(width, height, viewTransform, perspectiveTransform)
    if (currentParams != lastParams) {
      init(width, height)
      lastParams = currentParams
      val tr = {
        val p = new PerspectiveTransform()
        p.concatenate(perspectiveTransform.createInverse())
        p.concatenate(viewTransform)
        p.createInverse()
      }
      val srcPoint = new Point2D.Float()
      val dstPoint = new Point2D.Float()
      for (i <- 0 until height; j <- 0 until width) {
        srcPoint.setLocation(j, i)
        tr.transform(srcPoint, dstPoint)
        val x = dstPoint.x.toInt
        val y = dstPoint.y.toInt
        imageMemory.outputArray(i * width + j) =
          if (x >= 0 && x < imageWidth && y >= 0 && y < imageHeight)
            imageBuf(y * imageWidth + x)
          else
            0x00000000
      }
      imageMemory.outputImage.setRGB(0, 0, width, height, imageMemory.outputArray, 0, width)
    }
    imageMemory.outputImage
  }
}
