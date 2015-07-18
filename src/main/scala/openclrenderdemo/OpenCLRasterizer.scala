package openclrenderdemo

import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.{Files, StandardOpenOption}
import javax.media.jai.PerspectiveTransform

import openclrenderdemo.ImageRasterizer.Params
import openclrenderdemo.OpenCLMacros.callAndNull
import org.jocl.CL._
import org.jocl._

class OpenCLRasterizer extends ImageRasterizer {

  private var isInitialized = false

  private var context: cl_context = _

  private var program: cl_program = _

  private var queue: cl_command_queue = _

  private var rasterizeKernel: cl_kernel = _

  private var imageBuf: cl_mem = _

  private var (imageWidth, imageHeight) = (0, 0)

  private var matrixBuf: cl_mem = _

  private val programSource = Array(
    """
      |float4 mul_matrix(const __global float4 *matrix, float4 coords)
      |{
      |    float4 out = (float4)(
      |            dot(coords, matrix[0]),
      |            dot(coords, matrix[1]),
      |            dot(coords, matrix[2]),
      |            dot(coords, matrix[3]));
      |    return out / out.w;
      |}
      |
      |__kernel void rasterize(int2 out_size,
      |                        __global unsigned int* output_image,
      |                        int2 src_size,
      |                        __global const unsigned int* image,
      |                        __global const float4* matrix)
      |{
      |    int tid = get_global_id(0);
      |    if(tid >= out_size.x * out_size.y)
      |        return;
      |    int x = tid % out_size.x;
      |    int y = tid / out_size.x;
      |    float4 out_coords = (float4)(x, y, 0, 1);
      |    float4 src_coords = mul_matrix(matrix, out_coords);
      |    int2 xy = (int2)(src_coords.x, src_coords.y);
      |    output_image[tid] = xy.x >= 0 && xy.x < src_size.x && xy.y >= 0 && xy.y < src_size.y ?
      |            0xff000000 | image[xy.y * src_size.x + xy.x] :
      |            0x00000000;
      |}
    """.stripMargin)

  private object imageMemory {
    var outputImage: BufferedImage = _
    var (w, h) = (0, 0)
    var outputArray: Array[Int] = _
    var outputBuf: cl_mem = _

    def resize(w: Int, h: Int): Unit = {
      if (w != this.w || h != this.h) {
        callAndNull(clReleaseMemObject(outputBuf))
        outputBuf = clCreateBuffer(context, CL_MEM_WRITE_ONLY, w * h * Sizeof.cl_int, null, null)
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
      callAndNull(clReleaseMemObject(outputBuf))
    }
  }

  override def setImage(image: BufferedImage): Unit = {
    println("OpenCLRasterizer: setImage()...")
    init()
    callAndNull(clReleaseMemObject(imageBuf))
    val (w, h) = (image.getWidth, image.getHeight)
    val rgb = Array.ofDim[Int](w * h)
    image.getRGB(0, 0, w, h, rgb, 0, w)
    imageBuf = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, w * h * Sizeof.cl_int, Pointer to rgb, null)
    imageWidth = w
    imageHeight = h
  }

  def init(): Unit = {
    if (isInitialized)
      return
    println("OpenCLRasterizer: init..")
    val (platform, device) = OpenCLUtils.findDevice()
    val contextProps = new cl_context_properties
    contextProps.addProperty(CL_CONTEXT_PLATFORM, platform)
    context = clCreateContext(contextProps, 1, Array(device), null, null, null)
    queue = clCreateCommandQueue(context, device, 0, null)
    program = clCreateProgramWithSource(context, programSource.size, programSource, null, null)
    clBuildProgram(program, 0, null, null, null, null)
    rasterizeKernel = clCreateKernel(program, "rasterize", null)
    matrixBuf = {
      val arr = Array[Float](
        1, 0, 0, 0,
        0, 1, 0, 0,
        0, 0, 1, 0,
        0, 0, 0, 1)
      clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, 16 * Sizeof.cl_float, Pointer to arr, null)
    }
    isInitialized = true
    //    dumpProgram()
  }

  def release(): Unit = {
    imageMemory.release()
    callAndNull(clReleaseKernel(rasterizeKernel))
    callAndNull(clReleaseProgram(program))
    callAndNull(clReleaseCommandQueue(queue))
    callAndNull(clReleaseContext(context))
    callAndNull(clReleaseMemObject(imageBuf))
    imageWidth = 0
    imageHeight = 0
    isInitialized = false
    println("OpenCLRasterizer: released")
  }

  override def close(): Unit = release()

  override def finalize() = close()

  def init(w: Int, h: Int): Unit = {
    init()
    imageMemory.resize(w, h)
  }

  private var lastParams: Params = _

  override def rasterize(width: Int, height: Int, viewTransform: AffineTransform, perspectiveTransform: PerspectiveTransform): BufferedImage = {
    val currentParams = Params.create(width, height, viewTransform, perspectiveTransform)
    if (currentParams != lastParams) {
      init(width, height)
      lastParams = currentParams
      val matrix = {
        val p = new PerspectiveTransform()
        p.concatenate(perspectiveTransform.createInverse())
        p.concatenate(viewTransform)
        val pt = p.createInverse()
        val Array(a, b, c, d, e, f, g, h, i) = pt.getMatrix(null: Array[Array[Double]]).flatten.map(_.toFloat)
        Array[Float](
          a, b, 0, c,
          d, e, 0, f,
          0, 0, 1, 0,
          g, h, 0, i)
      }
      clEnqueueWriteBuffer(queue, matrixBuf, true, 0, 16 * Sizeof.cl_float, Pointer to matrix, 0, null, null)
      clSetKernelArg(rasterizeKernel, 0, Sizeof.cl_int2, Pointer to Array(imageMemory.w, imageMemory.h))
      clSetKernelArg(rasterizeKernel, 1, Sizeof.cl_mem, Pointer to imageMemory.outputBuf)
      clSetKernelArg(rasterizeKernel, 2, Sizeof.cl_int2, Pointer to Array(imageWidth, imageHeight))
      clSetKernelArg(rasterizeKernel, 3, Sizeof.cl_mem, Pointer to imageBuf)
      clSetKernelArg(rasterizeKernel, 4, Sizeof.cl_mem, Pointer to matrixBuf)
      val localWorkSize = 256L
      val globalWorkSize = OpenCLUtils.computeGlobalSize(width * height, localWorkSize)
      clEnqueueNDRangeKernel(queue, rasterizeKernel, 1, Array(0), Array(globalWorkSize), Array(localWorkSize), 0, null, null)
      clEnqueueReadBuffer(queue, imageMemory.outputBuf, true, 0, width * height * Sizeof.cl_int, Pointer to imageMemory.outputArray, 0, null, null)
      imageMemory.outputImage.setRGB(0, 0, width, height, imageMemory.outputArray, 0, width)
    }
    imageMemory.outputImage
  }

  def dumpProgram(): Unit = {
    val sizeBuf = Array(0L)
    clGetProgramInfo(program, CL_PROGRAM_BINARY_SIZES, Sizeof.cl_long, Pointer to sizeBuf, null)
    val size = sizeBuf(0).toInt
    val prog = Array.ofDim[Byte](size)
    val progPointer = Pointer to prog
    clGetProgramInfo(program, CL_PROGRAM_BINARIES, Sizeof.POINTER, Pointer to progPointer, null)
    val file = new File("data/opencl_out.txt")
    Files.write(file.toPath, prog, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    println(s"OpenCL program was written to '$file'")
  }
}
