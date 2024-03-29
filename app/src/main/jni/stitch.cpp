#include "jni.h"
#include <android/bitmap.h>
#include "opencv2/core.hpp"
#include "opencv2/imgproc.hpp"
#include "opencv2/calib3d.hpp"

#define ASSERT(status, ret)     if (!(status)) { return ret; }
#define ASSERT_FALSE(status)    ASSERT(status, false)

extern "C" {

bool BitmapToMatrix(JNIEnv *env, jobject obj_bitmap, cv::Mat &matrix) {
  void *bitmapPixels;
  AndroidBitmapInfo bitmapInfo;

  ASSERT_FALSE(AndroidBitmap_getInfo(env, obj_bitmap, &bitmapInfo) >= 0);
  ASSERT_FALSE(bitmapInfo.format == ANDROID_BITMAP_FORMAT_RGBA_8888);
  ASSERT_FALSE(AndroidBitmap_lockPixels(env, obj_bitmap, &bitmapPixels) >= 0);
  ASSERT_FALSE(bitmapPixels);

  cv::Mat tmp(bitmapInfo.height, bitmapInfo.width, CV_8UC4, bitmapPixels);
  tmp.copyTo(matrix);

  AndroidBitmap_unlockPixels(env, obj_bitmap);
  return true;
}

JNIEXPORT jboolean Java_soko_ekibun_stitch_Stitch_findHomographyNative(
    JNIEnv *env, jobject thiz,
    jobject img0, jobject img1, jboolean isdiff, jdoubleArray out) {
  cv::Mat mat0, mat1;
  BitmapToMatrix(env, img0, mat0);
  BitmapToMatrix(env, img1, mat1);
  cv::Mat kernel = (cv::Mat_<char>(3, 3) << 1, 1, 1, 1, -8, 1, 1, 1, 1);
  cv::Ptr <cv::DescriptorExtractor> surf = cv::SIFT::create();
  std::vector<cv::KeyPoint> kp0, kp1;
  if (mat0.rows == mat1.rows && mat0.cols == mat1.cols && isdiff) {
    cv::Mat grad0, grad1, diff;
    cv::filter2D(mat0, grad0, CV_8U, kernel);
    cv::filter2D(mat1, grad1, CV_8U, kernel);
    cv::absdiff(grad0, grad1, diff);
    cv::bitwise_and(grad0, diff, grad0);
    cv::bitwise_and(grad1, diff, grad1);
    surf->detect(grad0, kp0);
    surf->detect(grad1, kp1);
  } else {
    surf->detect(mat0, kp0);
    surf->detect(mat1, kp1);
  }
  cv::Mat desc0, desc1;
  surf->compute(mat0, kp0, desc0);
  surf->compute(mat1, kp1, desc1);
  cv::Ptr<cv::FlannBasedMatcher> matcher = cv::FlannBasedMatcher::create();
  std::vector<std::vector<cv::DMatch>> knnMatches;
  matcher->knnMatch(desc0, desc1, knnMatches, 2);
  std::vector<cv::Point2f> queryPoints, trainPoints;
  for (auto &knnMatch : knnMatches) {
    if (knnMatch[0].distance > 0.7 * knnMatch[1].distance) continue;
    queryPoints.push_back(kp0[knnMatch[0].queryIdx].pt);
    trainPoints.push_back(kp1[knnMatch[0].trainIdx].pt);
  }
  if (queryPoints.size() < 10) return false;
  cv::Mat homo = cv::findHomography(trainPoints, queryPoints, cv::RHO);
  jdouble *arr = (jdouble *) (homo.isContinuous() ? homo.data : homo.clone().data);
  uint length = homo.total() * homo.channels();
  env->SetDoubleArrayRegion(out, 0, length, arr);
  return true;
}

JNIEXPORT jboolean Java_soko_ekibun_stitch_Stitch_phaseCorrelateNative(
    JNIEnv *env, jobject thiz,
    jobject img0, jobject img1, jboolean isdiff, jdoubleArray out) {
  cv::Mat mat0, mat1;
  BitmapToMatrix(env, img0, mat0);
  BitmapToMatrix(env, img1, mat1);
  cv::Mat kernel = (cv::Mat_<char>(3, 3) << 1, 1, 1, 1, -8, 1, 1, 1, 1);
  cv::Mat grayL64F, grayR64F;
  if (mat0.rows != mat1.rows || mat0.cols != mat1.cols) {
    return false;
  }
  if (isdiff) {
    cv::Mat grad0, grad1, diff;
    cv::filter2D(mat0, grad0, CV_8U, kernel);
    cv::filter2D(mat1, grad1, CV_8U, kernel);
    cv::absdiff(grad0, grad1, diff);
    cv::bitwise_and(grad0, diff, grad0);
    cv::bitwise_and(grad1, diff, grad1);
    cvtColor(grad0, grad0, cv::COLOR_RGBA2GRAY);
    cvtColor(grad1, grad1, cv::COLOR_RGBA2GRAY);
    grad0.convertTo(grayL64F, CV_64F);
    grad1.convertTo(grayR64F, CV_64F);
  } else {
    cvtColor(mat0, mat0, cv::COLOR_RGBA2GRAY);
    cvtColor(mat1, mat1, cv::COLOR_RGBA2GRAY);
    mat0.convertTo(grayL64F, CV_64F);
    mat1.convertTo(grayR64F, CV_64F);
  }
  auto p = cv::phaseCorrelate(grayR64F, grayL64F);
  env->SetDoubleArrayRegion(out, 2, 1, &p.x);
  env->SetDoubleArrayRegion(out, 5, 1, &p.y);
  return true;
}
}