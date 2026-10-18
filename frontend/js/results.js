export function renderConnections(proposals, date, container) {
  const labels = { LAST_MINUTE: 'Najpóźniejszy pociąg', IDEAL: 'Z zapasem czasu', EARLY: 'Trochę wcześniej' };
  for (const proposal of proposals) {
    const article = document.createElement('article');
    article.className = 'connection';
    const heading = document.createElement('h2');
    const carrier = { SKMT: 'SKM', PR: 'Polregio (PR)' }[proposal.carrierCode] || proposal.carrierCode || 'Pociąg';
    const badge = document.createElement('span');
    badge.className = proposal.carrierCode === 'PR' ? 'carrier carrier-pr' : 'carrier';
    badge.textContent = carrier;
    const description = document.createElement('span');
    description.textContent = labels[proposal.type] || 'Połączenie';
    heading.append(badge, description);
    const stops = document.createElement('div');
    stops.className = 'connection-stops';
    const displayTime = (value) => value.slice(0, 10) === date ? value.slice(11, 16) : `${value.slice(11, 16)} (${value.slice(8, 10)}.${value.slice(5, 7)})`;
    for (const [label, time, platform, track] of [
      ['Odjazd', proposal.departureTime, proposal.departurePlatform, proposal.departureTrack],
      ['Przyjazd', proposal.arrivalTime, proposal.arrivalPlatform, proposal.arrivalTrack],
    ]) {
      const stop = document.createElement('div');
      const title = document.createElement('p');
      title.className = 'hint';
      title.textContent = label;
      const timeElement = document.createElement('p');
      timeElement.className = 'times';
      timeElement.textContent = displayTime(time);
      const location = document.createElement('p');
      location.className = 'stop-location';
      location.textContent = `Peron ${platform?.trim() || '—'} · Tor ${track?.trim() || '—'}`;
      if (!platform?.trim() || !track?.trim()) location.textContent += ' (— brak danych)';
      stop.append(title, timeElement, location);
      stops.append(stop);
    }
    const info = document.createElement('p');
    info.className = 'hint';
    info.textContent = `Pociąg ${proposal.trainNumber || '—'} · ${proposal.bufferMinutes} min przed wybraną godziną`;
    article.append(heading, stops, info);
    container.append(article);
  }
}
