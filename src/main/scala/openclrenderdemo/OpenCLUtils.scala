package openclrenderdemo

import java.io.{FileInputStream, IOException}
import java.util.Properties

import org.jocl.CL

object OpenCLUtils {
  println(s"OpenCL available: $isAvailable")

  def isAvailable: Boolean = {
    try {
      Class.forName("org.jocl.CL")
      CL.setExceptionsEnabled(true)
      true
    } catch {
      case e: UnsatisfiedLinkError => false
      case e: NoClassDefFoundError => false
    }
  }

  def findDevice() = {
    require(isAvailable)
    import org.jocl.CL._
    import org.jocl._
    val platformIndex = Config.PlatformIndex
    val deviceType = CL_DEVICE_TYPE_ALL
    val deviceIndex = Config.DeviceIndex
    val platforms = {
      val numPlatformsArray = Array(0)
      clGetPlatformIDs(0, null, numPlatformsArray)
      val pfs = Array.ofDim[cl_platform_id](numPlatformsArray(0))
      clGetPlatformIDs(pfs.size, pfs, null)
      pfs
    }
    val platform = platforms(platformIndex)
    val devices = {
      val nArr = Array(0)
      clGetDeviceIDs(platform, deviceType, 0, null, nArr)
      val dvs = Array.ofDim[cl_device_id](nArr(0))
      clGetDeviceIDs(platform, deviceType, dvs.size, dvs, null)
      dvs
    }
    val device = devices(deviceIndex)
    (platform, device)
  }

  def computeGlobalSize(n: Long, localSize: Long): Long =
    if (n % localSize == 0) n else (n / localSize + 1) * localSize


  object Config {
    private lazy val properties = {
      val p = new Properties()
      try {
        val ist = new FileInputStream("data/opencl.properties")
        try
          p.load(ist)
        finally
          ist.close()
      } catch {
        case _: IOException =>
      }
      p
    }
    lazy val PlatformIndex = properties.getProperty("opencl.platformindex", "0").toInt
    lazy val DeviceIndex = properties.getProperty("opencl.deviceindex", "0").toInt
  }

}
