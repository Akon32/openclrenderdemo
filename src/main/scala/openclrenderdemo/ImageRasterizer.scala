package openclrenderdemo

import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import javax.media.jai.PerspectiveTransform

trait ImageRasterizer {

  def setImage(image: BufferedImage)

  def rasterize(weight: Int, height: Int, viewTransform: AffineTransform, perspectiveTransform: PerspectiveTransform): BufferedImage

  def close()
}

object ImageRasterizer {

  def apply() = if (OpenCLUtils.isAvailable) new OpenCLRasterizer else new JavaRasterizer

  case class Params private(w: Int, h: Int, vt: AffineTransform, pt: PerspectiveTransform)

  object Params {
    def create(w: Int, h: Int, vt: AffineTransform, pt: PerspectiveTransform) =
      new Params(w, h, vt.clone().asInstanceOf[AffineTransform], pt.clone().asInstanceOf[PerspectiveTransform])
  }

}
