import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Topbar } from '../topbar/topbar';
import { Sidebar } from '../sidebar/sidebar';

@Component({
  selector: 'app-app-shell',
  imports: [RouterOutlet, Topbar, Sidebar],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.css'
})
export class AppShell {}
