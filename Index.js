export default {
  async fetch(request) {
    return new Response("Atelie Croche API funcionando!", {
      headers: {
        "Content-Type": "text/plain"
      }
    });
  }
};
