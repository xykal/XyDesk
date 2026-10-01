#include "streamxy.h"

#include <algorithm>
#include <atomic>
#include <cmath>
#include <vector>

struct sx_stats {
  explicit sx_stats(size_t cap) : buf(cap, 0.f) {}
  std::vector<float> buf;
  std::atomic<size_t> written{0};
};

extern "C" {

sx_stats* sx_stats_new(size_t capacity) { return new sx_stats(capacity ? capacity : 1); }

void sx_stats_free(sx_stats* s) { delete s; }

void sx_stats_push(sx_stats* s, float ms) {
  const size_t n = s->written.load(std::memory_order_relaxed);
  s->buf[n % s->buf.size()] = ms;
  s->written.store(n + 1, std::memory_order_release);
}

size_t sx_stats_count(const sx_stats* s) {
  return std::min(s->written.load(std::memory_order_acquire), s->buf.size());
}

float sx_stats_percentile(const sx_stats* s, float p) {
  const size_t n = sx_stats_count(s);
  if (n == 0) return NAN;
  std::vector<float> tmp(s->buf.begin(), s->buf.begin() + static_cast<long>(n));
  std::sort(tmp.begin(), tmp.end());
  const float rank = std::clamp(p, 0.f, 100.f) / 100.f * static_cast<float>(n - 1);
  const size_t lo = static_cast<size_t>(rank);
  const size_t hi = std::min(lo + 1, n - 1);
  const float frac = rank - static_cast<float>(lo);
  return tmp[lo] + (tmp[hi] - tmp[lo]) * frac;
}
}
